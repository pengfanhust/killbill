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

package com.ning.billing.junction.plumbing.billing;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.BillCycleDay;
import com.ning.billing.catalog.api.BillingAlignment;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.user.EffectiveSubscriptionEvent;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;

public class BillCycleDayCalculator {

    private static final Logger log = LoggerFactory.getLogger(BillCycleDayCalculator.class);

    private final CatalogService catalogService;
    private final EntitlementUserApi entitlementApi;

    @Inject
    public BillCycleDayCalculator(final CatalogService catalogService, final EntitlementUserApi entitlementApi) {
        this.catalogService = catalogService;
        this.entitlementApi = entitlementApi;
    }

    protected BillCycleDay calculateBcd(final SubscriptionBundle bundle, final Subscription subscription, final EffectiveSubscriptionEvent transition, final Account account)
            throws CatalogApiException, AccountApiException, EntitlementUserApiException {

        final Catalog catalog = catalogService.getFullCatalog();

        final Plan prevPlan = (transition.getPreviousPlan() != null) ? catalog.findPlan(transition.getPreviousPlan(), transition.getEffectiveTransitionTime(), transition.getSubscriptionStartDate()) : null;
        final Plan nextPlan = (transition.getNextPlan() != null) ? catalog.findPlan(transition.getNextPlan(), transition.getEffectiveTransitionTime(), transition.getSubscriptionStartDate()) : null;

        final Plan plan = (transition.getTransitionType() != SubscriptionTransitionType.CANCEL) ? nextPlan : prevPlan;
        final Product product = plan.getProduct();

        final PlanPhase prevPhase = (transition.getPreviousPhase() != null) ? catalog.findPhase(transition.getPreviousPhase(), transition.getEffectiveTransitionTime(), transition.getSubscriptionStartDate()) : null;
        final PlanPhase nextPhase = (transition.getNextPhase() != null) ? catalog.findPhase(transition.getNextPhase(), transition.getEffectiveTransitionTime(), transition.getSubscriptionStartDate()) : null;

        final PlanPhase phase = (transition.getTransitionType() != SubscriptionTransitionType.CANCEL) ? nextPhase : prevPhase;

        final BillingAlignment alignment = catalog.billingAlignment(
                new PlanPhaseSpecifier(product.getName(),
                                       product.getCategory(),
                                       phase.getBillingPeriod(),
                                       transition.getNextPriceList(),
                                       phase.getPhaseType()),
                transition.getRequestedTransitionTime());

        return calculateBcdForAlignment(alignment, bundle, subscription, account, catalog, plan);
    }

    @VisibleForTesting
    BillCycleDay calculateBcdForAlignment(final BillingAlignment alignment, final SubscriptionBundle bundle, final Subscription subscription,
                                          final Account account, final Catalog catalog, final Plan plan) throws AccountApiException, EntitlementUserApiException, CatalogApiException {
        BillCycleDay result = null;
        switch (alignment) {
            case ACCOUNT:
                result = account.getBillCycleDay();
                if (result == null || result.getDayOfMonthUTC() == 0) {
                    result = calculateBcdFromSubscription(subscription, plan, account, catalog);
                }
                break;
            case BUNDLE:
                // TODO
                final Subscription baseSub = entitlementApi.getBaseSubscription(bundle.getId(), null);
                Plan basePlan = baseSub.getCurrentPlan();
                if (basePlan == null) {
                    // The BP has been cancelled
                    final EffectiveSubscriptionEvent previousTransition = baseSub.getPreviousTransition();
                    basePlan = catalog.findPlan(previousTransition.getPreviousPlan(), previousTransition.getEffectiveTransitionTime(), previousTransition.getSubscriptionStartDate());
                }
                result = calculateBcdFromSubscription(baseSub, basePlan, account, catalog);
                break;
            case SUBSCRIPTION:
                result = calculateBcdFromSubscription(subscription, plan, account, catalog);
                break;
        }

        if (result == null) {
            throw new CatalogApiException(ErrorCode.CAT_INVALID_BILLING_ALIGNMENT, alignment.toString());
        }

        return result;
    }

    @VisibleForTesting
    BillCycleDay calculateBcdFromSubscription(final Subscription subscription, final Plan plan, final Account account, final Catalog catalog)
            throws AccountApiException, CatalogApiException {
        // Retrieve the initial phase type for that subscription
        // TODO - this should be extracted somewhere, along with this code above
        final PhaseType initialPhaseType;
        final List<EffectiveSubscriptionEvent> transitions = subscription.getAllTransitions();
        if (transitions.size() == 0) {
            initialPhaseType = null;
        } else {
            final DateTime requestedDate = subscription.getStartDate();
            final String initialPhaseString = transitions.get(0).getNextPhase();
            if (initialPhaseString == null) {
                initialPhaseType = null;
            } else {
                final PlanPhase initialPhase = catalog.findPhase(initialPhaseString, requestedDate, subscription.getStartDate());
                if (initialPhase == null) {
                    initialPhaseType = null;
                } else {
                    initialPhaseType = initialPhase.getPhaseType();
                }
            }
        }

        final DateTime date = plan.dateOfFirstRecurringNonZeroCharge(subscription.getStartDate(), initialPhaseType);
        // There are really two kinds of billCycleDay:
        // - a System billingCycleDay which should be computed from UTC time (in order to get the correct notification time at
        //   the end of each service period)
        // - a User billingCycleDay which should align with the account timezone
        final CalculatedBillCycleDay calculatedBillCycleDay = new CalculatedBillCycleDay(account.getTimeZone(), date);
        log.info("Calculated BCD: subscription id {}, subscription start {}, timezone {}, bcd UTC {}, bcd local {}",
                 new Object[]{subscription.getId(), date.toDateTimeISO(), account.getTimeZone(),
                              calculatedBillCycleDay.getDayOfMonthUTC(), calculatedBillCycleDay.getDayOfMonthLocal()});
        return calculatedBillCycleDay;
    }

    private static final class CalculatedBillCycleDay implements BillCycleDay {

        private final DateTime bcdTime;
        private final DateTimeZone accountTimeZone;

        private CalculatedBillCycleDay(final DateTimeZone accountTimeZone, final DateTime bcdTime) {
            this.accountTimeZone = accountTimeZone;
            this.bcdTime = bcdTime;
        }

        @Override
        public int getDayOfMonthUTC() {
            return bcdTime.toDateTime(DateTimeZone.UTC).getDayOfMonth();
        }

        @Override
        public int getDayOfMonthLocal() {
            return bcdTime.toDateTime(accountTimeZone).getDayOfMonth();
        }
    }
}
