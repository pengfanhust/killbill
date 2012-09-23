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

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.testng.Assert;

import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.entitlement.api.TestApiBase;
import com.ning.billing.entitlement.api.billing.EntitlementBillingApiException;
import com.ning.billing.util.clock.DefaultClock;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public abstract class TestUserApiCancel extends TestApiBase {
    protected void testCancelSubscriptionIMM() {
        try {
            final DateTime init = clock.getUTCNow();

            final String prod = "Shotgun";
            final BillingPeriod term = BillingPeriod.MONTHLY;
            final String planSet = PriceListSet.DEFAULT_PRICELIST_NAME;

            // CREATE
            final SubscriptionData subscription = createSubscription(prod, term, planSet);
            PlanPhase currentPhase = subscription.getCurrentPhase();
            assertEquals(currentPhase.getPhaseType(), PhaseType.TRIAL);

            // ADVANCE TIME still in trial
            final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(3));
            clock.addDeltaFromReality(it.toDurationMillis());

            final DateTime future = clock.getUTCNow();
            testListener.pushExpectedEvent(NextEvent.CANCEL);

            assertEquals(subscription.getLastActiveProductName(), prod);
            assertEquals(subscription.getLastActivePriceListName(), planSet);
            assertEquals(subscription.getLastActiveBillingPeriod(), term.toString());
            assertEquals(subscription.getLastActiveCategoryName(), "BASE");


            // CANCEL in trial period to get IMM policy
            subscription.cancel(clock.getUTCNow(), context);
            currentPhase = subscription.getCurrentPhase();
            testListener.isCompleted(3000);

            assertEquals(subscription.getLastActiveProductName(), prod);
            assertEquals(subscription.getLastActivePriceListName(), planSet);
            assertEquals(subscription.getLastActiveBillingPeriod(), term.toString());
            assertEquals(subscription.getLastActiveCategoryName(), "BASE");


            assertNull(currentPhase);
            checkNextPhaseChange(subscription, 0, null);

            assertListenerStatus();
        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }

    protected void testCancelSubscriptionEOTWithChargeThroughDate() throws EntitlementBillingApiException {
        try {
            final String prod = "Shotgun";
            final BillingPeriod term = BillingPeriod.MONTHLY;
            final String planSet = PriceListSet.DEFAULT_PRICELIST_NAME;

            // CREATE
            SubscriptionData subscription = createSubscription(prod, term, planSet);
            PlanPhase trialPhase = subscription.getCurrentPhase();
            assertEquals(trialPhase.getPhaseType(), PhaseType.TRIAL);

            // NEXT PHASE
            final DateTime expectedPhaseTrialChange = DefaultClock.addDuration(subscription.getStartDate(), trialPhase.getDuration());
            checkNextPhaseChange(subscription, 1, expectedPhaseTrialChange);

            // MOVE TO NEXT PHASE
            testListener.pushExpectedEvent(NextEvent.PHASE);
            Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(31));
            clock.addDeltaFromReality(it.toDurationMillis());

            assertTrue(testListener.isCompleted(5000));
            trialPhase = subscription.getCurrentPhase();
            assertEquals(trialPhase.getPhaseType(), PhaseType.EVERGREEN);

            // SET CTD + RE READ SUBSCRIPTION + CHANGE PLAN
            final Duration ctd = getDurationMonth(1);
            final DateTime newChargedThroughDate = DefaultClock.addDuration(expectedPhaseTrialChange, ctd);
            billingApi.setChargedThroughDate(subscription.getId(), newChargedThroughDate.toLocalDate(), context);
            subscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(subscription.getId());

            assertEquals(subscription.getLastActiveProductName(), prod);
            assertEquals(subscription.getLastActivePriceListName(), planSet);
            assertEquals(subscription.getLastActiveBillingPeriod(), term.toString());
            assertEquals(subscription.getLastActiveCategoryName(), "BASE");

            // CANCEL
            testListener.setNonExpectedMode();
            testListener.pushExpectedEvent(NextEvent.CANCEL);
            subscription.cancel(clock.getUTCNow(), context);
            assertFalse(testListener.isCompleted(3000));
            testListener.reset();


            assertEquals(subscription.getLastActiveProductName(), prod);
            assertEquals(subscription.getLastActivePriceListName(), planSet);
            assertEquals(subscription.getLastActiveBillingPeriod(), term.toString());
            assertEquals(subscription.getLastActiveCategoryName(), "BASE");

            DateTime futureEndDate = subscription.getFutureEndDate();
            Assert.assertNotNull(futureEndDate);

            // MOVE TO EOT + RECHECK
            testListener.pushExpectedEvent(NextEvent.CANCEL);
            it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(1));
            clock.addDeltaFromReality(it.toDurationMillis());
            final DateTime future = clock.getUTCNow();
            assertTrue(testListener.isCompleted(5000));

            assertTrue(futureEndDate.compareTo(subscription.getEndDate()) == 0);

            final PlanPhase currentPhase = subscription.getCurrentPhase();
            assertNull(currentPhase);
            checkNextPhaseChange(subscription, 0, null);

            assertEquals(subscription.getLastActiveProductName(), prod);
            assertEquals(subscription.getLastActivePriceListName(), planSet);
            assertEquals(subscription.getLastActiveBillingPeriod(), term.toString());
            assertEquals(subscription.getLastActiveCategoryName(), "BASE");


            assertListenerStatus();
        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }

    protected void testCancelSubscriptionEOTWithNoChargeThroughDate() {
        try {
            final String prod = "Shotgun";
            final BillingPeriod term = BillingPeriod.MONTHLY;
            final String planSet = PriceListSet.DEFAULT_PRICELIST_NAME;

            // CREATE
            final SubscriptionData subscription = createSubscription(prod, term, planSet);
            PlanPhase trialPhase = subscription.getCurrentPhase();
            assertEquals(trialPhase.getPhaseType(), PhaseType.TRIAL);

            // NEXT PHASE
            final DateTime expectedPhaseTrialChange = DefaultClock.addDuration(subscription.getStartDate(), trialPhase.getDuration());
            checkNextPhaseChange(subscription, 1, expectedPhaseTrialChange);

            // MOVE TO NEXT PHASE
            testListener.pushExpectedEvent(NextEvent.PHASE);

            final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(31));
            clock.addDeltaFromReality(it.toDurationMillis());
            assertTrue(testListener.isCompleted(5000));
            trialPhase = subscription.getCurrentPhase();
            assertEquals(trialPhase.getPhaseType(), PhaseType.EVERGREEN);

            testListener.pushExpectedEvent(NextEvent.CANCEL);

            // CANCEL
            subscription.cancel(clock.getUTCNow(), context);
            assertTrue(testListener.isCompleted(5000));

            final PlanPhase currentPhase = subscription.getCurrentPhase();
            assertNull(currentPhase);
            checkNextPhaseChange(subscription, 0, null);

            assertListenerStatus();
        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }

    // Similar test to testCancelSubscriptionEOTWithChargeThroughDate except we uncancel and check things
    // are as they used to be and we can move forward without hitting cancellation
    //
    protected void testUncancel() throws EntitlementBillingApiException {
        try {
            final String prod = "Shotgun";
            final BillingPeriod term = BillingPeriod.MONTHLY;
            final String planSet = PriceListSet.DEFAULT_PRICELIST_NAME;

            // CREATE
            SubscriptionData subscription = createSubscription(prod, term, planSet);
            final PlanPhase trialPhase = subscription.getCurrentPhase();
            assertEquals(trialPhase.getPhaseType(), PhaseType.TRIAL);

            // NEXT PHASE
            final DateTime expectedPhaseTrialChange = DefaultClock.addDuration(subscription.getStartDate(), trialPhase.getDuration());
            checkNextPhaseChange(subscription, 1, expectedPhaseTrialChange);

            // MOVE TO NEXT PHASE
            testListener.pushExpectedEvent(NextEvent.PHASE);
            Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(31));
            clock.addDeltaFromReality(it.toDurationMillis());
            assertTrue(testListener.isCompleted(5000));
            PlanPhase currentPhase = subscription.getCurrentPhase();
            assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);

            // SET CTD + RE READ SUBSCRIPTION + CHANGE PLAN
            final Duration ctd = getDurationMonth(1);
            final DateTime newChargedThroughDate = DefaultClock.addDuration(expectedPhaseTrialChange, ctd);
            billingApi.setChargedThroughDate(subscription.getId(), newChargedThroughDate.toLocalDate(), context);
            subscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(subscription.getId());

            // CANCEL EOT
            subscription.cancel(clock.getUTCNow(), context);

            subscription.uncancel(context);

            // MOVE TO EOT + RECHECK
            testListener.pushExpectedEvent(NextEvent.UNCANCEL);
            it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(1));
            clock.addDeltaFromReality(it.toDurationMillis());
            assertTrue(testListener.isCompleted(5000));

            final Plan currentPlan = subscription.getCurrentPlan();
            assertEquals(currentPlan.getProduct().getName(), prod);
            currentPhase = subscription.getCurrentPhase();
            assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);

            assertListenerStatus();
        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }
}
