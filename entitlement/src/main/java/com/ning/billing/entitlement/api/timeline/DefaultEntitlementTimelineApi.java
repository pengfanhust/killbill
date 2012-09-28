/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.entitlement.api.timeline;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.SubscriptionFactory;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline.NewEvent;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.api.user.EffectiveSubscriptionEvent;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionBundleData;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.glue.DefaultEntitlementModule;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.TenantContext;

import com.google.inject.Inject;
import com.google.inject.name.Named;

public class DefaultEntitlementTimelineApi implements EntitlementTimelineApi {

    private final EntitlementDao dao;
    private final SubscriptionFactory factory;
    private final RepairEntitlementLifecycleDao repairDao;
    private final CatalogService catalogService;
    private final InternalCallContextFactory internalCallContextFactory;

    private enum RepairType {
        BASE_REPAIR,
        ADD_ON_REPAIR,
        STANDALONE_REPAIR
    }

    @Inject
    public DefaultEntitlementTimelineApi(@Named(DefaultEntitlementModule.REPAIR_NAMED) final SubscriptionFactory factory, final CatalogService catalogService,
                                         @Named(DefaultEntitlementModule.REPAIR_NAMED) final RepairEntitlementLifecycleDao repairDao, final EntitlementDao dao,
                                         final InternalCallContextFactory internalCallContextFactory) {
        this.catalogService = catalogService;
        this.dao = dao;
        this.repairDao = repairDao;
        this.factory = factory;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public BundleTimeline getBundleTimeline(final SubscriptionBundle bundle, final TenantContext context)
            throws EntitlementRepairException {
        return getBundleTimelineInternal(bundle, bundle.getKey(), context);
    }

    @Override
    public BundleTimeline getBundleTimeline(final UUID accountId, final String bundleName, final TenantContext context)
            throws EntitlementRepairException {
        final SubscriptionBundle bundle = dao.getSubscriptionBundleFromAccountAndKey(accountId, bundleName, internalCallContextFactory.createInternalTenantContext(accountId, context));
        return getBundleTimelineInternal(bundle, bundleName + " [accountId= " + accountId.toString() + "]", context);
    }

    @Override
    public BundleTimeline getBundleTimeline(final UUID bundleId, final TenantContext context) throws EntitlementRepairException {

        final SubscriptionBundle bundle = dao.getSubscriptionBundleFromId(bundleId, internalCallContextFactory.createInternalTenantContext(context));
        return getBundleTimelineInternal(bundle, bundleId.toString(), context);
    }

    private BundleTimeline getBundleTimelineInternal(final SubscriptionBundle bundle, final String descBundle, final TenantContext context) throws EntitlementRepairException {
        try {
            if (bundle == null) {
                throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_UNKNOWN_BUNDLE, descBundle);
            }
            final List<Subscription> subscriptions = dao.getSubscriptions(factory, bundle.getId(), internalCallContextFactory.createInternalTenantContext(context));
            if (subscriptions.size() == 0) {
                throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_NO_ACTIVE_SUBSCRIPTIONS, bundle.getId());
            }
            final String viewId = getViewId(((SubscriptionBundleData) bundle).getLastSysUpdateTime(), subscriptions);
            final List<SubscriptionTimeline> repairs = createGetSubscriptionRepairList(subscriptions, Collections.<SubscriptionTimeline>emptyList());
            return createGetBundleRepair(bundle.getId(), bundle.getKey(), viewId, repairs);
        } catch (CatalogApiException e) {
            throw new EntitlementRepairException(e);
        }
    }

    @Override
    public BundleTimeline repairBundle(final BundleTimeline input, final boolean dryRun, final CallContext context) throws EntitlementRepairException {
        final InternalTenantContext tenantContext = internalCallContextFactory.createInternalTenantContext(context);
        try {
            final SubscriptionBundle bundle = dao.getSubscriptionBundleFromId(input.getBundleId(), tenantContext);
            if (bundle == null) {
                throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_UNKNOWN_BUNDLE, input.getBundleId());
            }

            // Subscriptions are ordered with BASE subscription first-- if exists
            final List<Subscription> subscriptions = dao.getSubscriptions(factory, input.getBundleId(), tenantContext);
            if (subscriptions.size() == 0) {
                throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_NO_ACTIVE_SUBSCRIPTIONS, input.getBundleId());
            }

            final String viewId = getViewId(((SubscriptionBundleData) bundle).getLastSysUpdateTime(), subscriptions);
            if (!viewId.equals(input.getViewId())) {
                throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_VIEW_CHANGED, input.getBundleId(), input.getViewId(), viewId);
            }

            DateTime firstDeletedBPEventTime = null;
            DateTime lastRemainingBPEventTime = null;

            boolean isBasePlanRecreate = false;
            DateTime newBundleStartDate = null;

            SubscriptionDataRepair baseSubscriptionRepair = null;
            final List<SubscriptionDataRepair> addOnSubscriptionInRepair = new LinkedList<SubscriptionDataRepair>();
            final List<SubscriptionDataRepair> inRepair = new LinkedList<SubscriptionDataRepair>();
            for (final Subscription cur : subscriptions) {
                final SubscriptionTimeline curRepair = findAndCreateSubscriptionRepair(cur.getId(), input.getSubscriptions());
                if (curRepair != null) {
                    final SubscriptionDataRepair curInputRepair = ((SubscriptionDataRepair) cur);
                    final List<EntitlementEvent> remaining = getRemainingEventsAndValidateDeletedEvents(curInputRepair, firstDeletedBPEventTime, curRepair.getDeletedEvents());

                    final boolean isPlanRecreate = (curRepair.getNewEvents().size() > 0
                                                    && (curRepair.getNewEvents().get(0).getSubscriptionTransitionType() == SubscriptionTransitionType.CREATE
                                                        || curRepair.getNewEvents().get(0).getSubscriptionTransitionType() == SubscriptionTransitionType.RE_CREATE));

                    final DateTime newSubscriptionStartDate = isPlanRecreate ? curRepair.getNewEvents().get(0).getRequestedDate() : null;

                    if (isPlanRecreate && remaining.size() != 0) {
                        throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_SUB_RECREATE_NOT_EMPTY, cur.getId(), cur.getBundleId());
                    }

                    if (!isPlanRecreate && remaining.size() == 0) {
                        throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_SUB_EMPTY, cur.getId(), cur.getBundleId());
                    }

                    if (cur.getCategory() == ProductCategory.BASE) {

                        final int bpTransitionSize = ((SubscriptionData) cur).getAllTransitions().size();
                        lastRemainingBPEventTime = (remaining.size() > 0) ? curInputRepair.getAllTransitions().get(remaining.size() - 1).getEffectiveTransitionTime() : null;
                        firstDeletedBPEventTime = (remaining.size() < bpTransitionSize) ? curInputRepair.getAllTransitions().get(remaining.size()).getEffectiveTransitionTime() : null;

                        isBasePlanRecreate = isPlanRecreate;
                        newBundleStartDate = newSubscriptionStartDate;
                    }

                    if (curRepair.getNewEvents().size() > 0) {
                        final DateTime lastRemainingEventTime = (remaining.size() == 0) ? null : curInputRepair.getAllTransitions().get(remaining.size() - 1).getEffectiveTransitionTime();
                        validateFirstNewEvent(curInputRepair, curRepair.getNewEvents().get(0), lastRemainingBPEventTime, lastRemainingEventTime);
                    }

                    final SubscriptionDataRepair curOutputRepair = createSubscriptionDataRepair(curInputRepair, newBundleStartDate, newSubscriptionStartDate, remaining);
                    repairDao.initializeRepair(curInputRepair.getId(), remaining, tenantContext);
                    inRepair.add(curOutputRepair);
                    if (curOutputRepair.getCategory() == ProductCategory.ADD_ON) {
                        // Check if ADD_ON RE_CREATE is before BP start
                        if (isPlanRecreate && (subscriptions.get(0)).getStartDate().isAfter(curRepair.getNewEvents().get(0).getRequestedDate())) {
                            throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_AO_CREATE_BEFORE_BP_START, cur.getId(), cur.getBundleId());
                        }
                        addOnSubscriptionInRepair.add(curOutputRepair);
                    } else if (curOutputRepair.getCategory() == ProductCategory.BASE) {
                        baseSubscriptionRepair = curOutputRepair;
                    }
                }
            }

            final RepairType repairType = getRepairType(subscriptions.get(0), (baseSubscriptionRepair != null));
            switch (repairType) {
                case BASE_REPAIR:
                    // We need to add any existing addon that are not in the input repair list
                    for (final Subscription cur : subscriptions) {
                        if (cur.getCategory() == ProductCategory.ADD_ON && !inRepair.contains(cur)) {
                            final SubscriptionDataRepair curOutputRepair = createSubscriptionDataRepair((SubscriptionDataRepair) cur, newBundleStartDate, null, ((SubscriptionDataRepair) cur).getEvents());
                            repairDao.initializeRepair(curOutputRepair.getId(), ((SubscriptionDataRepair) cur).getEvents(), tenantContext);
                            inRepair.add(curOutputRepair);
                            addOnSubscriptionInRepair.add(curOutputRepair);
                        }
                    }
                    break;
                case ADD_ON_REPAIR:
                    // We need to set the baseSubscription as it is useful to calculate addon validity
                    final SubscriptionDataRepair baseSubscription = (SubscriptionDataRepair) subscriptions.get(0);
                    baseSubscriptionRepair = createSubscriptionDataRepair(baseSubscription, baseSubscription.getBundleStartDate(), baseSubscription.getAlignStartDate(), baseSubscription.getEvents());
                    break;
                case STANDALONE_REPAIR:
                default:
                    break;
            }

            validateBasePlanRecreate(isBasePlanRecreate, subscriptions, input.getSubscriptions());
            validateInputSubscriptionsKnown(subscriptions, input.getSubscriptions());

            final Collection<NewEvent> newEvents = createOrderedNewEventInput(input.getSubscriptions());
            for (final NewEvent newEvent : newEvents) {
                final DefaultNewEvent cur = (DefaultNewEvent) newEvent;
                final SubscriptionDataRepair curDataRepair = findSubscriptionDataRepair(cur.getSubscriptionId(), inRepair);
                if (curDataRepair == null) {
                    throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_UNKNOWN_SUBSCRIPTION, cur.getSubscriptionId());
                }
                curDataRepair.addNewRepairEvent(cur, baseSubscriptionRepair, addOnSubscriptionInRepair, context);
            }

            if (dryRun) {
                baseSubscriptionRepair.addFutureAddonCancellation(addOnSubscriptionInRepair, context);

                final List<SubscriptionTimeline> repairs = createGetSubscriptionRepairList(subscriptions, convertDataRepair(inRepair));
                return createGetBundleRepair(input.getBundleId(), bundle.getKey(), input.getViewId(), repairs);
            } else {
                dao.repair(bundle.getAccountId(), input.getBundleId(), inRepair, internalCallContextFactory.createInternalCallContext(context));
                return getBundleTimeline(input.getBundleId(), context);
            }
        } catch (CatalogApiException e) {
            throw new EntitlementRepairException(e);
        } finally {
            repairDao.cleanup(tenantContext);
        }
    }

    private RepairType getRepairType(final Subscription firstSubscription, final boolean gotBaseSubscription) {
        if (firstSubscription.getCategory() == ProductCategory.BASE) {
            return gotBaseSubscription ? RepairType.BASE_REPAIR : RepairType.ADD_ON_REPAIR;
        } else {
            return RepairType.STANDALONE_REPAIR;
        }
    }

    private void validateBasePlanRecreate(final boolean isBasePlanRecreate, final List<Subscription> subscriptions, final List<SubscriptionTimeline> input)
            throws EntitlementRepairException {
        if (!isBasePlanRecreate) {
            return;
        }
        if (subscriptions.size() != input.size()) {
            throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_BP_RECREATE_MISSING_AO, subscriptions.get(0).getBundleId());
        }
        for (final SubscriptionTimeline cur : input) {
            if (cur.getNewEvents().size() != 0
                && (cur.getNewEvents().get(0).getSubscriptionTransitionType() != SubscriptionTransitionType.CREATE
                    && cur.getNewEvents().get(0).getSubscriptionTransitionType() != SubscriptionTransitionType.RE_CREATE)) {
                throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_BP_RECREATE_MISSING_AO_CREATE, subscriptions.get(0).getBundleId());
            }
        }
    }

    private void validateInputSubscriptionsKnown(final List<Subscription> subscriptions, final List<SubscriptionTimeline> input)
            throws EntitlementRepairException {
        for (final SubscriptionTimeline cur : input) {
            boolean found = false;
            for (final Subscription s : subscriptions) {
                if (s.getId().equals(cur.getId())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_UNKNOWN_SUBSCRIPTION, cur.getId());
            }
        }
    }

    private void validateFirstNewEvent(final SubscriptionData data, final NewEvent firstNewEvent, final DateTime lastBPRemainingTime, final DateTime lastRemainingTime)
            throws EntitlementRepairException {
        if (lastBPRemainingTime != null &&
            firstNewEvent.getRequestedDate().isBefore(lastBPRemainingTime)) {
            throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_NEW_EVENT_BEFORE_LAST_BP_REMAINING, firstNewEvent.getSubscriptionTransitionType(), data.getId());
        }
        if (lastRemainingTime != null &&
            firstNewEvent.getRequestedDate().isBefore(lastRemainingTime)) {
            throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_NEW_EVENT_BEFORE_LAST_AO_REMAINING, firstNewEvent.getSubscriptionTransitionType(), data.getId());
        }

    }

    private Collection<NewEvent> createOrderedNewEventInput(final List<SubscriptionTimeline> subscriptionsReapir) {
        final TreeSet<NewEvent> newEventSet = new TreeSet<SubscriptionTimeline.NewEvent>(new Comparator<NewEvent>() {
            @Override
            public int compare(final NewEvent o1, final NewEvent o2) {
                return o1.getRequestedDate().compareTo(o2.getRequestedDate());
            }
        });
        for (final SubscriptionTimeline cur : subscriptionsReapir) {
            for (final NewEvent e : cur.getNewEvents()) {
                newEventSet.add(new DefaultNewEvent(cur.getId(), e.getPlanPhaseSpecifier(), e.getRequestedDate(), e.getSubscriptionTransitionType()));
            }
        }

        return newEventSet;
    }

    private List<EntitlementEvent> getRemainingEventsAndValidateDeletedEvents(final SubscriptionDataRepair data, final DateTime firstBPDeletedTime,
                                                                              final List<SubscriptionTimeline.DeletedEvent> deletedEvents)
            throws EntitlementRepairException {
        if (deletedEvents == null || deletedEvents.size() == 0) {
            return data.getEvents();
        }

        int nbDeleted = 0;
        final LinkedList<EntitlementEvent> result = new LinkedList<EntitlementEvent>();
        for (final EntitlementEvent cur : data.getEvents()) {

            boolean foundDeletedEvent = false;
            for (final SubscriptionTimeline.DeletedEvent d : deletedEvents) {
                if (cur.getId().equals(d.getEventId())) {
                    foundDeletedEvent = true;
                    nbDeleted++;
                    break;
                }
            }
            if (!foundDeletedEvent && nbDeleted > 0) {
                throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_INVALID_DELETE_SET, cur.getId(), data.getId());
            }
            if (firstBPDeletedTime != null &&
                !cur.getEffectiveDate().isBefore(firstBPDeletedTime) &&
                !foundDeletedEvent) {
                throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_MISSING_AO_DELETE_EVENT, cur.getId(), data.getId());
            }

            if (nbDeleted == 0) {
                result.add(cur);
            }
        }

        if (nbDeleted != deletedEvents.size()) {
            for (final SubscriptionTimeline.DeletedEvent d : deletedEvents) {
                boolean found = false;
                for (final EffectiveSubscriptionEvent cur : data.getAllTransitions()) {
                    if (cur.getId().equals(d.getEventId())) {
                        found = true;
                    }
                }
                if (!found) {
                    throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_NON_EXISTENT_DELETE_EVENT, d.getEventId(), data.getId());
                }
            }

        }

        return result;
    }

    private String getViewId(final DateTime lastUpdateBundleDate, final List<Subscription> subscriptions) {
        final StringBuilder tmp = new StringBuilder();
        long lastOrderedId = -1;
        for (final Subscription cur : subscriptions) {
            lastOrderedId = lastOrderedId < ((SubscriptionData) cur).getLastEventOrderedId() ? ((SubscriptionData) cur).getLastEventOrderedId() : lastOrderedId;
        }
        tmp.append(lastOrderedId);
        tmp.append("-");
        tmp.append(lastUpdateBundleDate.toDate().getTime());

        return tmp.toString();
    }

    private BundleTimeline createGetBundleRepair(final UUID bundleId, final String externalKey, final String viewId, final List<SubscriptionTimeline> repairList) {
        return new BundleTimeline() {
            @Override
            public String getViewId() {
                return viewId;
            }

            @Override
            public List<SubscriptionTimeline> getSubscriptions() {
                return repairList;
            }

            @Override
            public UUID getBundleId() {
                return bundleId;
            }

            @Override
            public String getExternalKey() {
                return externalKey;
            }
        };
    }

    private List<SubscriptionTimeline> createGetSubscriptionRepairList(final List<Subscription> subscriptions, final List<SubscriptionTimeline> inRepair) throws CatalogApiException {

        final List<SubscriptionTimeline> result = new LinkedList<SubscriptionTimeline>();
        final Set<UUID> repairIds = new TreeSet<UUID>();
        for (final SubscriptionTimeline cur : inRepair) {
            repairIds.add(cur.getId());
            result.add(cur);
        }

        for (final Subscription cur : subscriptions) {
            if (!repairIds.contains(cur.getId())) {
                result.add(new DefaultSubscriptionTimeline((SubscriptionDataRepair) cur, catalogService.getFullCatalog()));
            }
        }

        return result;
    }

    private List<SubscriptionTimeline> convertDataRepair(final List<SubscriptionDataRepair> input) throws CatalogApiException {
        final List<SubscriptionTimeline> result = new LinkedList<SubscriptionTimeline>();
        for (final SubscriptionDataRepair cur : input) {
            result.add(new DefaultSubscriptionTimeline(cur, catalogService.getFullCatalog()));
        }

        return result;
    }

    private SubscriptionDataRepair findSubscriptionDataRepair(final UUID targetId, final List<SubscriptionDataRepair> input) {
        for (final SubscriptionDataRepair cur : input) {
            if (cur.getId().equals(targetId)) {
                return cur;
            }
        }

        return null;
    }

    private SubscriptionDataRepair createSubscriptionDataRepair(final SubscriptionData curData, final DateTime newBundleStartDate, final DateTime newSubscriptionStartDate, final List<EntitlementEvent> initialEvents) {
        final SubscriptionBuilder builder = new SubscriptionBuilder(curData);
        builder.setActiveVersion(curData.getActiveVersion() + 1);
        if (newBundleStartDate != null) {
            builder.setBundleStartDate(newBundleStartDate);
        }
        if (newSubscriptionStartDate != null) {
            builder.setAlignStartDate(newSubscriptionStartDate);
        }
        if (initialEvents.size() > 0) {
            for (final EntitlementEvent cur : initialEvents) {
                cur.setActiveVersion(builder.getActiveVersion());
            }
        }

        return (SubscriptionDataRepair) factory.createSubscription(builder, initialEvents);
    }

    private SubscriptionTimeline findAndCreateSubscriptionRepair(final UUID target, final List<SubscriptionTimeline> input) {
        for (final SubscriptionTimeline cur : input) {
            if (target.equals(cur.getId())) {
                return new DefaultSubscriptionTimeline(cur);
            }
        }

        return null;
    }
}

