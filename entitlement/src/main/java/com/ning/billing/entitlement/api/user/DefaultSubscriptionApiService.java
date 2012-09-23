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

package com.ning.billing.entitlement.api.user;

import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.ActionPolicy;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanChangeResult;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PlanSpecifier;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.entitlement.alignment.PlanAligner;
import com.ning.billing.entitlement.alignment.TimedPhase;
import com.ning.billing.entitlement.api.SubscriptionApiService;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.phase.PhaseEvent;
import com.ning.billing.entitlement.events.phase.PhaseEventData;
import com.ning.billing.entitlement.events.user.ApiEvent;
import com.ning.billing.entitlement.events.user.ApiEventBuilder;
import com.ning.billing.entitlement.events.user.ApiEventCancel;
import com.ning.billing.entitlement.events.user.ApiEventChange;
import com.ning.billing.entitlement.events.user.ApiEventCreate;
import com.ning.billing.entitlement.events.user.ApiEventReCreate;
import com.ning.billing.entitlement.events.user.ApiEventUncancel;
import com.ning.billing.entitlement.exceptions.EntitlementError;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.DefaultClock;

import com.google.inject.Inject;

public class DefaultSubscriptionApiService implements SubscriptionApiService {

    private final Clock clock;
    private final EntitlementDao dao;
    private final CatalogService catalogService;
    private final PlanAligner planAligner;

    @Inject
    public DefaultSubscriptionApiService(final Clock clock, final EntitlementDao dao, final CatalogService catalogService, final PlanAligner planAligner) {
        this.clock = clock;
        this.catalogService = catalogService;
        this.planAligner = planAligner;
        this.dao = dao;
    }

    @Override
    public SubscriptionData createPlan(final SubscriptionBuilder builder, final Plan plan, final PhaseType initialPhase,
                                       final String realPriceList, final DateTime requestedDate, final DateTime effectiveDate, final DateTime processedDate,
                                       final CallContext context) throws EntitlementUserApiException {
        final SubscriptionData subscription = new SubscriptionData(builder, this, clock);

        createFromSubscription(subscription, plan, initialPhase, realPriceList, requestedDate, effectiveDate, processedDate, false, context);
        return subscription;
    }

    @Override
    public boolean recreatePlan(final SubscriptionData subscription, final PlanPhaseSpecifier spec, final DateTime requestedDateWithMs, final CallContext context)
            throws EntitlementUserApiException {
        final SubscriptionState currentState = subscription.getState();
        if (currentState != null && currentState != SubscriptionState.CANCELLED) {
            throw new EntitlementUserApiException(ErrorCode.ENT_RECREATE_BAD_STATE, subscription.getId(), currentState);
        }

        final DateTime now = clock.getUTCNow();
        final DateTime requestedDate = (requestedDateWithMs != null) ? DefaultClock.truncateMs(requestedDateWithMs) : now;
        validateRequestedDate(subscription, now, requestedDate);

        try {
            final String realPriceList = (spec.getPriceListName() == null) ? PriceListSet.DEFAULT_PRICELIST_NAME : spec.getPriceListName();
            final Plan plan = catalogService.getFullCatalog().findPlan(spec.getProductName(), spec.getBillingPeriod(), realPriceList, requestedDate);
            final PlanPhase phase = plan.getAllPhases()[0];
            if (phase == null) {
                throw new EntitlementError(String.format("No initial PlanPhase for Product %s, term %s and set %s does not exist in the catalog",
                                                         spec.getProductName(), spec.getBillingPeriod().toString(), realPriceList));
            }

            final DateTime effectiveDate = requestedDate;
            final DateTime processedDate = now;

            createFromSubscription(subscription, plan, spec.getPhaseType(), realPriceList, requestedDate, effectiveDate, processedDate, true, context);
            return true;
        } catch (CatalogApiException e) {
            throw new EntitlementUserApiException(e);
        }
    }

    private void createFromSubscription(final SubscriptionData subscription, final Plan plan, final PhaseType initialPhase,
                                        final String realPriceList, final DateTime requestedDate, final DateTime effectiveDate, final DateTime processedDate,
                                        final boolean reCreate, final CallContext context) throws EntitlementUserApiException {
        try {
            final TimedPhase[] curAndNextPhases = planAligner.getCurrentAndNextTimedPhaseOnCreate(subscription, plan, initialPhase, realPriceList, requestedDate, effectiveDate);

            final ApiEventBuilder createBuilder = new ApiEventBuilder()
            .setSubscriptionId(subscription.getId())
            .setEventPlan(plan.getName())
            .setEventPlanPhase(curAndNextPhases[0].getPhase().getName())
            .setEventPriceList(realPriceList)
            .setActiveVersion(subscription.getActiveVersion())
            .setProcessedDate(processedDate)
            .setEffectiveDate(effectiveDate)
            .setRequestedDate(requestedDate)
            .setUserToken(context.getUserToken())
            .setFromDisk(true);
    final ApiEvent creationEvent = (reCreate) ? new ApiEventReCreate(createBuilder) : new ApiEventCreate(createBuilder);

            final TimedPhase nextTimedPhase = curAndNextPhases[1];
            final PhaseEvent nextPhaseEvent = (nextTimedPhase != null) ?
                                              PhaseEventData.createNextPhaseEvent(nextTimedPhase.getPhase().getName(), subscription, processedDate, nextTimedPhase.getStartPhase()) :
                                              null;
            final List<EntitlementEvent> events = new ArrayList<EntitlementEvent>();
            events.add(creationEvent);
            if (nextPhaseEvent != null) {
                events.add(nextPhaseEvent);
            }
            if (reCreate) {
                dao.recreateSubscription(subscription, events, context);
            } else {
                dao.createSubscription(subscription, events, context);
            }
            subscription.rebuildTransitions(dao.getEventsForSubscription(subscription.getId()), catalogService.getFullCatalog());
        } catch (CatalogApiException e) {
            throw new EntitlementUserApiException(e);
        }
    }

    @Override
    public boolean cancel(final SubscriptionData subscription, final DateTime requestedDateWithMs, final CallContext context) throws EntitlementUserApiException {
        try {
            final SubscriptionState currentState = subscription.getState();
            if (currentState != SubscriptionState.ACTIVE) {
                throw new EntitlementUserApiException(ErrorCode.ENT_CANCEL_BAD_STATE, subscription.getId(), currentState);
            }
            final DateTime now = clock.getUTCNow();
            final DateTime requestedDate = (requestedDateWithMs != null) ? DefaultClock.truncateMs(requestedDateWithMs) : now;

            final Plan currentPlan = subscription.getCurrentPlan();
            final PlanPhaseSpecifier planPhase = new PlanPhaseSpecifier(currentPlan.getProduct().getName(),
                                                                        currentPlan.getProduct().getCategory(),
                                                                        subscription.getCurrentPlan().getBillingPeriod(),
                                                                        subscription.getCurrentPriceList().getName(),
                                                                        subscription.getCurrentPhase().getPhaseType());

            final ActionPolicy policy = catalogService.getFullCatalog().planCancelPolicy(planPhase, requestedDate);

            return doCancelPlan(subscription, requestedDateWithMs, now, policy, context);
        } catch (CatalogApiException e) {
            throw new EntitlementUserApiException(e);
        }
    }

    @Override
    public boolean cancelWithPolicy(final SubscriptionData subscription, final DateTime requestedDateWithMs, final ActionPolicy policy, final CallContext context) throws EntitlementUserApiException {
        final SubscriptionState currentState = subscription.getState();
        if (currentState != SubscriptionState.ACTIVE) {
            throw new EntitlementUserApiException(ErrorCode.ENT_CANCEL_BAD_STATE, subscription.getId(), currentState);
        }
        final DateTime now = clock.getUTCNow();
        return doCancelPlan(subscription, requestedDateWithMs, now, policy, context);
    }

    private boolean doCancelPlan(final SubscriptionData subscription, final DateTime requestedDateWithMs, final DateTime now, final ActionPolicy policy, final CallContext context) throws EntitlementUserApiException {

        final DateTime requestedDate = (requestedDateWithMs != null) ? DefaultClock.truncateMs(requestedDateWithMs) : now;
        validateRequestedDate(subscription, now, requestedDate);
        final DateTime effectiveDate = subscription.getPlanChangeEffectiveDate(policy, requestedDate);

        final EntitlementEvent cancelEvent = new ApiEventCancel(new ApiEventBuilder()
        .setSubscriptionId(subscription.getId())
        .setActiveVersion(subscription.getActiveVersion())
        .setProcessedDate(now)
        .setEffectiveDate(effectiveDate)
        .setRequestedDate(requestedDate)
        .setUserToken(context.getUserToken())
        .setFromDisk(true));

        dao.cancelSubscription(subscription, cancelEvent, context, 0);
        subscription.rebuildTransitions(dao.getEventsForSubscription(subscription.getId()), catalogService.getFullCatalog());
        return (policy == ActionPolicy.IMMEDIATE);
    }


    @Override
    public boolean uncancel(final SubscriptionData subscription, final CallContext context) throws EntitlementUserApiException {
        if (!subscription.isSubscriptionFutureCancelled()) {
            throw new EntitlementUserApiException(ErrorCode.ENT_UNCANCEL_BAD_STATE, subscription.getId().toString());
        }

        final DateTime now = clock.getUTCNow();
        final EntitlementEvent uncancelEvent = new ApiEventUncancel(new ApiEventBuilder()
                                                                            .setSubscriptionId(subscription.getId())
                                                                            .setActiveVersion(subscription.getActiveVersion())
                                                                            .setProcessedDate(now)
                                                                            .setRequestedDate(now)
                                                                            .setEffectiveDate(now)
                                                                            .setUserToken(context.getUserToken())
                                                                            .setFromDisk(true));

        final List<EntitlementEvent> uncancelEvents = new ArrayList<EntitlementEvent>();
        uncancelEvents.add(uncancelEvent);

        final TimedPhase nextTimedPhase = planAligner.getNextTimedPhase(subscription, now, now);
        final PhaseEvent nextPhaseEvent = (nextTimedPhase != null) ?
                                          PhaseEventData.createNextPhaseEvent(nextTimedPhase.getPhase().getName(), subscription, now, nextTimedPhase.getStartPhase()) :
                                          null;
        if (nextPhaseEvent != null) {
            uncancelEvents.add(nextPhaseEvent);
        }

        dao.uncancelSubscription(subscription, uncancelEvents, context);
        subscription.rebuildTransitions(dao.getEventsForSubscription(subscription.getId()), catalogService.getFullCatalog());

        return true;
    }

    @Override
    public boolean changePlan(final SubscriptionData subscription, final String productName, final BillingPeriod term,
                              final String priceList, final DateTime requestedDateWithMs, final CallContext context)
            throws EntitlementUserApiException {
        final DateTime now = clock.getUTCNow();
        final DateTime requestedDate = (requestedDateWithMs != null) ? DefaultClock.truncateMs(requestedDateWithMs) : now;

        validateRequestedDate(subscription, now, requestedDate);
        validateSubscriptionState(subscription);

        final PlanChangeResult planChangeResult = getPlanChangeResult(subscription, productName, term, priceList, requestedDate);
        final ActionPolicy policy = planChangeResult.getPolicy();

        try {
            return doChangePlan(subscription, planChangeResult, now, requestedDate, productName, term, policy, context);
        } catch (CatalogApiException e) {
            throw new EntitlementUserApiException(e);
        }
    }

    @Override
    public boolean changePlanWithPolicy(final SubscriptionData subscription, final String productName, final BillingPeriod term,
                                        final String priceList, final DateTime requestedDateWithMs, final ActionPolicy policy, final CallContext context)
            throws EntitlementUserApiException {
        final DateTime now = clock.getUTCNow();
        final DateTime requestedDate = (requestedDateWithMs != null) ? DefaultClock.truncateMs(requestedDateWithMs) : now;

        validateRequestedDate(subscription, now, requestedDate);
        validateSubscriptionState(subscription);

        final PlanChangeResult planChangeResult = getPlanChangeResult(subscription, productName, term, priceList, requestedDate);

        try {
            return doChangePlan(subscription, planChangeResult, now, requestedDate, productName, term, policy, context);
        } catch (CatalogApiException e) {
            throw new EntitlementUserApiException(e);
        }
    }

    private PlanChangeResult getPlanChangeResult(final SubscriptionData subscription, final String productName,
                                                 final BillingPeriod term, final String priceList, final DateTime requestedDate) throws EntitlementUserApiException {
        final PlanChangeResult planChangeResult;
        try {
            final Product destProduct = catalogService.getFullCatalog().findProduct(productName, requestedDate);
            final Plan currentPlan = subscription.getCurrentPlan();
            final PriceList currentPriceList = subscription.getCurrentPriceList();
            final PlanPhaseSpecifier fromPlanPhase = new PlanPhaseSpecifier(currentPlan.getProduct().getName(),
                                                                            currentPlan.getProduct().getCategory(),
                                                                            currentPlan.getBillingPeriod(),
                                                                            currentPriceList.getName(),
                                                                            subscription.getCurrentPhase().getPhaseType());
            final PlanSpecifier toPlanPhase = new PlanSpecifier(productName,
                                                                destProduct.getCategory(),
                                                                term,
                                                                priceList);

            planChangeResult = catalogService.getFullCatalog().planChange(fromPlanPhase, toPlanPhase, requestedDate);
        } catch (CatalogApiException e) {
            throw new EntitlementUserApiException(e);
        }

        return planChangeResult;
    }

    private boolean doChangePlan(final SubscriptionData subscription, final PlanChangeResult planChangeResult,
                                 final DateTime now, final DateTime requestedDate, final String productName,
                                 final BillingPeriod term, final ActionPolicy policy, final CallContext context) throws EntitlementUserApiException, CatalogApiException {
        final PriceList newPriceList = planChangeResult.getNewPriceList();

        final Plan newPlan = catalogService.getFullCatalog().findPlan(productName, term, newPriceList.getName(), requestedDate, subscription.getStartDate());
        final DateTime effectiveDate = subscription.getPlanChangeEffectiveDate(policy, requestedDate);

        final TimedPhase currentTimedPhase = planAligner.getCurrentTimedPhaseOnChange(subscription, newPlan, newPriceList.getName(), requestedDate, effectiveDate);

        final EntitlementEvent changeEvent = new ApiEventChange(new ApiEventBuilder()
        .setSubscriptionId(subscription.getId())
        .setEventPlan(newPlan.getName())
        .setEventPlanPhase(currentTimedPhase.getPhase().getName())
        .setEventPriceList(newPriceList.getName())
        .setActiveVersion(subscription.getActiveVersion())
        .setProcessedDate(now)
        .setEffectiveDate(effectiveDate)
        .setRequestedDate(requestedDate)
        .setUserToken(context.getUserToken())
        .setFromDisk(true));

        final TimedPhase nextTimedPhase = planAligner.getNextTimedPhaseOnChange(subscription, newPlan, newPriceList.getName(), requestedDate, effectiveDate);
        final PhaseEvent nextPhaseEvent = (nextTimedPhase != null) ?
                                          PhaseEventData.createNextPhaseEvent(nextTimedPhase.getPhase().getName(), subscription, now, nextTimedPhase.getStartPhase()) :
                                          null;

        final List<EntitlementEvent> changeEvents = new ArrayList<EntitlementEvent>();
        // Only add the PHASE if it does not coincide with the CHANGE, if not this is 'just' a CHANGE.
        if (nextPhaseEvent != null && !nextPhaseEvent.getEffectiveDate().equals(changeEvent.getEffectiveDate())) {
            changeEvents.add(nextPhaseEvent);
        }
        changeEvents.add(changeEvent);

        dao.changePlan(subscription, changeEvents, context);
        subscription.rebuildTransitions(dao.getEventsForSubscription(subscription.getId()), catalogService.getFullCatalog());

        return (policy == ActionPolicy.IMMEDIATE);
    }

    private void validateRequestedDate(final SubscriptionData subscription, final DateTime now, final DateTime requestedDate)
            throws EntitlementUserApiException {

        if (requestedDate.isAfter(now)) {
            throw new EntitlementUserApiException(ErrorCode.ENT_INVALID_REQUESTED_FUTURE_DATE, requestedDate.toString());
        }

        final EffectiveSubscriptionEvent previousTransition = subscription.getPreviousTransition();
        if (previousTransition != null && previousTransition.getEffectiveTransitionTime().isAfter(requestedDate)) {
            throw new EntitlementUserApiException(ErrorCode.ENT_INVALID_REQUESTED_DATE,
                                                  requestedDate.toString(), previousTransition.getEffectiveTransitionTime());
        }
    }

    private void validateSubscriptionState(final SubscriptionData subscription) throws EntitlementUserApiException {
        final SubscriptionState currentState = subscription.getState();
        if (currentState != SubscriptionState.ACTIVE) {
            throw new EntitlementUserApiException(ErrorCode.ENT_CHANGE_NON_ACTIVE, subscription.getId(), currentState);
        }
        if (subscription.isSubscriptionFutureCancelled()) {
            throw new EntitlementUserApiException(ErrorCode.ENT_CHANGE_FUTURE_CANCELLED, subscription.getId());
        }
    }
}
