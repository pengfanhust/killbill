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

package com.ning.billing.entitlement.api.migration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.alignment.MigrationPlanAligner;
import com.ning.billing.entitlement.alignment.TimedMigration;
import com.ning.billing.entitlement.api.SubscriptionFactory;
import com.ning.billing.entitlement.api.migration.AccountMigrationData.BundleMigrationData;
import com.ning.billing.entitlement.api.migration.AccountMigrationData.SubscriptionMigrationData;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.api.user.SubscriptionBundleData;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.EntitlementEvent.EventType;
import com.ning.billing.entitlement.events.phase.PhaseEvent;
import com.ning.billing.entitlement.events.phase.PhaseEventData;
import com.ning.billing.entitlement.events.user.ApiEvent;
import com.ning.billing.entitlement.events.user.ApiEventBuilder;
import com.ning.billing.entitlement.events.user.ApiEventCancel;
import com.ning.billing.entitlement.events.user.ApiEventChange;
import com.ning.billing.entitlement.events.user.ApiEventMigrateBilling;
import com.ning.billing.entitlement.events.user.ApiEventMigrateEntitlement;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.entitlement.exceptions.EntitlementError;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.clock.Clock;

public class DefaultEntitlementMigrationApi implements EntitlementMigrationApi {
    private final EntitlementDao dao;
    private final MigrationPlanAligner migrationAligner;
    private final SubscriptionFactory factory;
    private final Clock clock;

    @Inject
    public DefaultEntitlementMigrationApi(final MigrationPlanAligner migrationAligner,
                                          final SubscriptionFactory factory,
                                          final EntitlementDao dao,
                                          final Clock clock) {
        this.dao = dao;
        this.migrationAligner = migrationAligner;
        this.factory = factory;
        this.clock = clock;
    }

    @Override
    public void migrate(final EntitlementAccountMigration toBeMigrated, final CallContext context)
            throws EntitlementMigrationApiException {
        final AccountMigrationData accountMigrationData = createAccountMigrationData(toBeMigrated, context);
        dao.migrate(toBeMigrated.getAccountKey(), accountMigrationData, context);
    }

    private AccountMigrationData createAccountMigrationData(final EntitlementAccountMigration toBeMigrated, final CallContext context)
            throws EntitlementMigrationApiException {
        final UUID accountId = toBeMigrated.getAccountKey();
        final DateTime now = clock.getUTCNow();

        final List<BundleMigrationData> accountBundleData = new LinkedList<BundleMigrationData>();

        for (final EntitlementBundleMigration curBundle : toBeMigrated.getBundles()) {

            final SubscriptionBundleData bundleData = new SubscriptionBundleData(curBundle.getBundleKey(), accountId, clock.getUTCNow());
            final List<SubscriptionMigrationData> bundleSubscriptionData = new LinkedList<AccountMigrationData.SubscriptionMigrationData>();

            final List<EntitlementSubscriptionMigration> sortedSubscriptions = Lists.newArrayList(curBundle.getSubscriptions());
            // Make sure we have first BASE or STANDALONE, then ADDON and for each category order by CED
            Collections.sort(sortedSubscriptions, new Comparator<EntitlementSubscriptionMigration>() {
                @Override
                public int compare(final EntitlementSubscriptionMigration o1,
                                   final EntitlementSubscriptionMigration o2) {
                    if (o1.getCategory().equals(o2.getCategory())) {
                        return o1.getSubscriptionCases()[0].getEffectiveDate().compareTo(o2.getSubscriptionCases()[0].getEffectiveDate());
                    } else {
                        if (!o1.getCategory().name().equalsIgnoreCase("ADD_ON")) {
                            return -1;
                        } else if (o1.getCategory().name().equalsIgnoreCase("ADD_ON")) {
                            return 1;
                        } else {
                            return 0;
                        }
                    }
                }
            });

            DateTime bundleStartDate = null;
            for (final EntitlementSubscriptionMigration curSub : sortedSubscriptions) {
                SubscriptionMigrationData data = null;
                if (bundleStartDate == null) {
                    data = createInitialSubscription(bundleData.getId(), curSub.getCategory(), curSub.getSubscriptionCases(), now, curSub.getChargedThroughDate(), context);
                    bundleStartDate = data.getInitialEvents().get(0).getEffectiveDate();
                } else {
                    data = createSubscriptionMigrationDataWithBundleDate(bundleData.getId(), curSub.getCategory(), curSub.getSubscriptionCases(), now,
                                                                         bundleStartDate, curSub.getChargedThroughDate(), context);
                }
                if (data != null) {
                    bundleSubscriptionData.add(data);
                }
            }
            final BundleMigrationData bundleMigrationData = new BundleMigrationData(bundleData, bundleSubscriptionData);
            accountBundleData.add(bundleMigrationData);
        }

        return new AccountMigrationData(accountBundleData);
    }

    private SubscriptionMigrationData createInitialSubscription(final UUID bundleId, final ProductCategory productCategory,
                                                                final EntitlementSubscriptionMigrationCase[] input, final DateTime now, final DateTime ctd, final CallContext context)
            throws EntitlementMigrationApiException {
        final TimedMigration[] events = migrationAligner.getEventsMigration(input, now);
        final DateTime migrationStartDate = events[0].getEventTime();
        final List<EntitlementEvent> emptyEvents = Collections.emptyList();
        final SubscriptionData subscriptionData = factory.createSubscription(new SubscriptionBuilder()
                                                                                     .setId(UUID.randomUUID())
                                                                                     .setBundleId(bundleId)
                                                                                     .setCategory(productCategory)
                                                                                     .setBundleStartDate(migrationStartDate)
                                                                                     .setAlignStartDate(migrationStartDate),
                                                                             emptyEvents);
        return new SubscriptionMigrationData(subscriptionData, toEvents(subscriptionData, now, ctd, events, context), ctd);
    }

    private SubscriptionMigrationData createSubscriptionMigrationDataWithBundleDate(final UUID bundleId, final ProductCategory productCategory,
                                                                                    final EntitlementSubscriptionMigrationCase[] input, final DateTime now, final DateTime bundleStartDate, final DateTime ctd, final CallContext context)
            throws EntitlementMigrationApiException {
        final TimedMigration[] events = migrationAligner.getEventsMigration(input, now);
        final DateTime migrationStartDate = events[0].getEventTime();
        final List<EntitlementEvent> emptyEvents = Collections.emptyList();
        final SubscriptionData subscriptionData = factory.createSubscription(new SubscriptionBuilder()
                                                                                     .setId(UUID.randomUUID())
                                                                                     .setBundleId(bundleId)
                                                                                     .setCategory(productCategory)
                                                                                     .setBundleStartDate(bundleStartDate)
                                                                                     .setAlignStartDate(migrationStartDate),
                                                                             emptyEvents);
        return new SubscriptionMigrationData(subscriptionData, toEvents(subscriptionData, now, ctd, events, context), ctd);
    }

    private List<EntitlementEvent> toEvents(final SubscriptionData subscriptionData, final DateTime now, final DateTime ctd, final TimedMigration[] migrationEvents, final CallContext context) {
        ApiEventMigrateEntitlement creationEvent = null;
        final List<EntitlementEvent> events = new ArrayList<EntitlementEvent>(migrationEvents.length);
        DateTime subsciptionCancelledDate = null;
        for (final TimedMigration cur : migrationEvents) {

            if (cur.getEventType() == EventType.PHASE) {
                final PhaseEvent nextPhaseEvent = PhaseEventData.createNextPhaseEvent(cur.getPhase().getName(), subscriptionData, now, cur.getEventTime());
                events.add(nextPhaseEvent);

            } else if (cur.getEventType() == EventType.API_USER) {

                final ApiEventBuilder builder = new ApiEventBuilder()
                        .setSubscriptionId(subscriptionData.getId())
                        .setEventPlan((cur.getPlan() != null) ? cur.getPlan().getName() : null)
                        .setEventPlanPhase((cur.getPhase() != null) ? cur.getPhase().getName() : null)
                        .setEventPriceList(cur.getPriceList())
                        .setActiveVersion(subscriptionData.getActiveVersion())
                        .setEffectiveDate(cur.getEventTime())
                        .setProcessedDate(now)
                        .setRequestedDate(now)
                        .setUserToken(context.getUserToken())
                        .setFromDisk(true);

                switch (cur.getApiEventType()) {
                    case MIGRATE_ENTITLEMENT:
                        creationEvent = new ApiEventMigrateEntitlement(builder);
                        events.add(creationEvent);
                        break;

                    case CHANGE:
                        events.add(new ApiEventChange(builder));
                        break;
                    case CANCEL:
                        subsciptionCancelledDate = cur.getEventTime();
                        events.add(new ApiEventCancel(builder));
                        break;
                    default:
                        throw new EntitlementError(String.format("Unexpected type of api migration event %s", cur.getApiEventType()));
                }
            } else {
                throw new EntitlementError(String.format("Unexpected type of migration event %s", cur.getEventType()));
            }
        }
        if (creationEvent == null || ctd == null) {
            throw new EntitlementError(String.format("Could not create migration billing event ctd = %s", ctd));
        }
        if (subsciptionCancelledDate == null || subsciptionCancelledDate.isAfter(ctd)) {
            events.add(new ApiEventMigrateBilling(creationEvent, ctd));
        }
        Collections.sort(events, new Comparator<EntitlementEvent>() {
            int compForApiType(final EntitlementEvent o1, final EntitlementEvent o2, final ApiEventType type) {
                ApiEventType apiO1 = null;
                if (o1.getType() == EventType.API_USER) {
                    apiO1 = ((ApiEvent) o1).getEventType();
                }
                ApiEventType apiO2 = null;
                if (o2.getType() == EventType.API_USER) {
                    apiO2 = ((ApiEvent) o2).getEventType();
                }
                if (apiO1 != null && apiO1.equals(type)) {
                    return -1;
                } else if (apiO2 != null && apiO2.equals(type)) {
                    return 1;
                } else {
                    return 0;
                }
            }

            @Override
            public int compare(final EntitlementEvent o1, final EntitlementEvent o2) {

                int comp = o1.getEffectiveDate().compareTo(o2.getEffectiveDate());
                if (comp == 0) {
                    comp = compForApiType(o1, o2, ApiEventType.MIGRATE_ENTITLEMENT);
                }
                if (comp == 0) {
                    comp = compForApiType(o1, o2, ApiEventType.MIGRATE_BILLING);
                }
                return comp;
            }
        });

        return events;
    }
}
