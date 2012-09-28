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
import org.joda.time.Interval;
import org.testng.Assert;

import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.TestApiBase;
import com.ning.billing.entitlement.api.billing.EntitlementBillingApiException;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.user.ApiEvent;
import com.ning.billing.util.clock.DefaultClock;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public abstract class TestUserApiChangePlan extends TestApiBase {
    private void checkChangePlan(final SubscriptionData subscription, final String expProduct, final ProductCategory expCategory,
                                 final BillingPeriod expBillingPeriod, final PhaseType expPhase) {

        final Plan currentPlan = subscription.getCurrentPlan();
        assertNotNull(currentPlan);
        assertEquals(currentPlan.getProduct().getName(), expProduct);
        assertEquals(currentPlan.getProduct().getCategory(), expCategory);
        assertEquals(currentPlan.getBillingPeriod(), expBillingPeriod);

        final PlanPhase currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), expPhase);
    }

    protected void testChangePlanBundleAlignEOTWithNoChargeThroughDate() {
        tChangePlanBundleAlignEOTWithNoChargeThroughDate("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, "Pistol", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);
    }

    private void tChangePlanBundleAlignEOTWithNoChargeThroughDate(final String fromProd, final BillingPeriod fromTerm, final String fromPlanSet,
                                                                  final String toProd, final BillingPeriod toTerm, final String toPlanSet) {
        try {
            // CREATE
            final SubscriptionData subscription = createSubscription(fromProd, fromTerm, fromPlanSet);

            // MOVE TO NEXT PHASE
            PlanPhase currentPhase = subscription.getCurrentPhase();
            testListener.pushExpectedEvent(NextEvent.PHASE);

            final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(31));
            clock.addDeltaFromReality(it.toDurationMillis());

            final DateTime futureNow = clock.getUTCNow();
            final DateTime nextExpectedPhaseChange = DefaultClock.addDuration(subscription.getStartDate(), currentPhase.getDuration());
            assertTrue(futureNow.isAfter(nextExpectedPhaseChange));
            assertTrue(testListener.isCompleted(5000));

            // CHANGE PLAN
            testListener.pushExpectedEvent(NextEvent.CHANGE);
            subscription.changePlan(toProd, toTerm, toPlanSet, clock.getUTCNow(), callContext);
            assertTrue(testListener.isCompleted(5000));

            // CHECK CHANGE PLAN
            currentPhase = subscription.getCurrentPhase();
            checkChangePlan(subscription, toProd, ProductCategory.BASE, toTerm, PhaseType.EVERGREEN);

            assertListenerStatus();
        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }

    protected void testChangePlanBundleAlignEOTWithChargeThroughDate() throws EntitlementBillingApiException {
        testChangePlanBundleAlignEOTWithChargeThroughDate("Shotgun", BillingPeriod.ANNUAL, "gunclubDiscount", "Pistol", BillingPeriod.ANNUAL, "gunclubDiscount");
    }

    private void testChangePlanBundleAlignEOTWithChargeThroughDate(final String fromProd, final BillingPeriod fromTerm, final String fromPlanSet,
                                                                   final String toProd, final BillingPeriod toTerm, final String toPlanSet) throws EntitlementBillingApiException {
        try {
            // CREATE
            SubscriptionData subscription = createSubscription(fromProd, fromTerm, fromPlanSet);
            final PlanPhase trialPhase = subscription.getCurrentPhase();
            final DateTime expectedPhaseTrialChange = DefaultClock.addDuration(subscription.getStartDate(), trialPhase.getDuration());
            assertEquals(trialPhase.getPhaseType(), PhaseType.TRIAL);

            // MOVE TO NEXT PHASE
            testListener.pushExpectedEvent(NextEvent.PHASE);
            Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(31));
            clock.addDeltaFromReality(it.toDurationMillis());
            assertTrue(testListener.isCompleted(5000));
            PlanPhase currentPhase = subscription.getCurrentPhase();
            assertEquals(currentPhase.getPhaseType(), PhaseType.DISCOUNT);

            // SET CTD
            final Duration ctd = getDurationMonth(1);
            final DateTime newChargedThroughDate = DefaultClock.addDuration(expectedPhaseTrialChange, ctd);
            billingApi.setChargedThroughDate(subscription.getId(), newChargedThroughDate.toLocalDate(), callContext);

            // RE READ SUBSCRIPTION + CHANGE PLAN
            testListener.setNonExpectedMode();
            testListener.pushExpectedEvent(NextEvent.CHANGE);
            subscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(subscription.getId(), callContext);
            subscription.changePlan(toProd, toTerm, toPlanSet, clock.getUTCNow(), callContext);
            assertFalse(testListener.isCompleted(3000));
            testListener.reset();

            // CHECK CHANGE PLAN
            currentPhase = subscription.getCurrentPhase();
            checkChangePlan(subscription, fromProd, ProductCategory.BASE, fromTerm, PhaseType.DISCOUNT);

            // NEXT PHASE
            final DateTime nextExpectedPhaseChange = DefaultClock.addDuration(expectedPhaseTrialChange, currentPhase.getDuration());
            checkNextPhaseChange(subscription, 2, nextExpectedPhaseChange);

            // ALSO VERIFY PENDING CHANGE EVENT
            final List<EntitlementEvent> events = dao.getPendingEventsForSubscription(subscription.getId(), internalCallContext);
            assertTrue(events.get(0) instanceof ApiEvent);

            // MOVE TO EOT
            testListener.pushExpectedEvent(NextEvent.CHANGE);
            it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(1));
            clock.addDeltaFromReality(it.toDurationMillis());
            assertTrue(testListener.isCompleted(5000));

            subscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(subscription.getId(), callContext);
            currentPhase = subscription.getCurrentPhase();
            checkChangePlan(subscription, toProd, ProductCategory.BASE, toTerm, PhaseType.DISCOUNT);

            assertListenerStatus();
        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }

    protected void testChangePlanBundleAlignIMM() {
        tChangePlanBundleAlignIMM("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, "Assault-Rifle", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);
    }

    private void tChangePlanBundleAlignIMM(final String fromProd, final BillingPeriod fromTerm, final String fromPlanSet,
                                           final String toProd, final BillingPeriod toTerm, final String toPlanSet) {

        try {
            final SubscriptionData subscription = createSubscription(fromProd, fromTerm, fromPlanSet);

            testListener.pushExpectedEvent(NextEvent.CHANGE);

            Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(3));
            clock.addDeltaFromReality(it.toDurationMillis());

            // CHANGE PLAN IMM
            subscription.changePlan(toProd, toTerm, toPlanSet, clock.getUTCNow(), callContext);
            checkChangePlan(subscription, toProd, ProductCategory.BASE, toTerm, PhaseType.TRIAL);

            assertTrue(testListener.isCompleted(5000));

            final PlanPhase currentPhase = subscription.getCurrentPhase();
            final DateTime nextExpectedPhaseChange = DefaultClock.addDuration(subscription.getStartDate(), currentPhase.getDuration());
            checkNextPhaseChange(subscription, 1, nextExpectedPhaseChange);

            // NEXT PHASE
            testListener.pushExpectedEvent(NextEvent.PHASE);
            it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(30));
            clock.addDeltaFromReality(it.toDurationMillis());
            final DateTime futureNow = clock.getUTCNow();

            assertTrue(futureNow.isAfter(nextExpectedPhaseChange));
            assertTrue(testListener.isCompleted(5000));

            assertListenerStatus();
        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }

    protected void testChangePlanChangePlanAlignEOTWithChargeThroughDate() throws EntitlementBillingApiException {
        tChangePlanChangePlanAlignEOTWithChargeThroughDate("Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, "Assault-Rifle", BillingPeriod.ANNUAL, "rescue");
    }

    private void tChangePlanChangePlanAlignEOTWithChargeThroughDate(final String fromProd, final BillingPeriod fromTerm, final String fromPlanSet,
                                                                    final String toProd, final BillingPeriod toTerm, final String toPlanSet) throws EntitlementBillingApiException {
        try {
            DateTime currentTime = clock.getUTCNow();

            SubscriptionData subscription = createSubscription(fromProd, fromTerm, fromPlanSet);
            final PlanPhase trialPhase = subscription.getCurrentPhase();
            final DateTime expectedPhaseTrialChange = DefaultClock.addDuration(subscription.getStartDate(), trialPhase.getDuration());
            assertEquals(trialPhase.getPhaseType(), PhaseType.TRIAL);

            // MOVE TO NEXT PHASE
            testListener.pushExpectedEvent(NextEvent.PHASE);
            currentTime = clock.getUTCNow();
            Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(31));
            clock.addDeltaFromReality(it.toDurationMillis());
            currentTime = clock.getUTCNow();
            assertTrue(testListener.isCompleted(5000));

            // SET CTD
            final Duration ctd = getDurationMonth(1);
            final DateTime newChargedThroughDate = DefaultClock.addDuration(expectedPhaseTrialChange, ctd);
            billingApi.setChargedThroughDate(subscription.getId(), newChargedThroughDate.toLocalDate(), callContext);

            // RE READ SUBSCRIPTION + CHECK CURRENT PHASE
            subscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(subscription.getId(), callContext);
            PlanPhase currentPhase = subscription.getCurrentPhase();
            assertNotNull(currentPhase);
            assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);

            // CHANGE PLAN
            currentTime = clock.getUTCNow();
            subscription.changePlan(toProd, toTerm, toPlanSet, clock.getUTCNow(), callContext);

            checkChangePlan(subscription, fromProd, ProductCategory.BASE, fromTerm, PhaseType.EVERGREEN);

            // CHECK CHANGE DID NOT KICK IN YET
            testListener.setNonExpectedMode();
            testListener.pushExpectedEvent(NextEvent.CHANGE);
            assertFalse(testListener.isCompleted(3000));
            testListener.reset();

            // MOVE TO AFTER CTD
            testListener.pushExpectedEvent(NextEvent.CHANGE);
            it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(1));
            clock.addDeltaFromReality(it.toDurationMillis());
            currentTime = clock.getUTCNow();
            assertTrue(testListener.isCompleted(5000));

            // CHECK CORRECT PRODUCT, PHASE, PLAN SET
            final String currentProduct = subscription.getCurrentPlan().getProduct().getName();
            assertNotNull(currentProduct);
            assertEquals(currentProduct, toProd);
            currentPhase = subscription.getCurrentPhase();
            assertNotNull(currentPhase);
            assertEquals(currentPhase.getPhaseType(), PhaseType.DISCOUNT);

            // MOVE TIME ABOUT ONE MONTH BEFORE NEXT EXPECTED PHASE CHANGE
            testListener.setNonExpectedMode();
            testListener.pushExpectedEvent(NextEvent.PHASE);
            it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(11));
            clock.addDeltaFromReality(it.toDurationMillis());
            currentTime = clock.getUTCNow();
            assertFalse(testListener.isCompleted(3000));
            testListener.reset();

            final DateTime nextExpectedPhaseChange = DefaultClock.addDuration(newChargedThroughDate, currentPhase.getDuration());
            checkNextPhaseChange(subscription, 1, nextExpectedPhaseChange);

            // MOVE TIME RIGHT AFTER NEXT EXPECTED PHASE CHANGE
            testListener.pushExpectedEvent(NextEvent.PHASE);
            it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(1));
            clock.addDeltaFromReality(it.toDurationMillis());

            currentTime = clock.getUTCNow();
            assertTrue(testListener.isCompleted(5000));

            assertListenerStatus();
        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }

    protected void testMultipleChangeLastIMM() throws EntitlementBillingApiException {
        try {
            SubscriptionData subscription = createSubscription("Assault-Rifle", BillingPeriod.MONTHLY, "gunclubDiscount");
            final PlanPhase trialPhase = subscription.getCurrentPhase();
            assertEquals(trialPhase.getPhaseType(), PhaseType.TRIAL);

            // MOVE TO NEXT PHASE
            testListener.pushExpectedEvent(NextEvent.PHASE);
            final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(31));
            clock.addDeltaFromReality(it.toDurationMillis());

            assertTrue(testListener.isCompleted(5000));

            // SET CTD
            final List<Duration> durationList = new ArrayList<Duration>();
            durationList.add(trialPhase.getDuration());
            //durationList.add(subscription.getCurrentPhase().getDuration());
            final DateTime startDiscountPhase = DefaultClock.addDuration(subscription.getStartDate(), durationList);
            final Duration ctd = getDurationMonth(1);
            final DateTime newChargedThroughDate = DefaultClock.addDuration(startDiscountPhase, ctd);
            billingApi.setChargedThroughDate(subscription.getId(), newChargedThroughDate.toLocalDate(), callContext);
            subscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(subscription.getId(), callContext);

            // CHANGE EOT
            testListener.setNonExpectedMode();
            testListener.pushExpectedEvent(NextEvent.CHANGE);
            subscription.changePlan("Pistol", BillingPeriod.MONTHLY, "gunclubDiscount", clock.getUTCNow(), callContext);
            assertFalse(testListener.isCompleted(3000));
            testListener.reset();

            // CHANGE
            testListener.pushExpectedEvent(NextEvent.CHANGE);
            subscription.changePlan("Assault-Rifle", BillingPeriod.ANNUAL, "gunclubDiscount", clock.getUTCNow(), callContext);
            assertTrue(testListener.isCompleted(5000));

            final Plan currentPlan = subscription.getCurrentPlan();
            assertNotNull(currentPlan);
            assertEquals(currentPlan.getProduct().getName(), "Assault-Rifle");
            assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
            assertEquals(currentPlan.getBillingPeriod(), BillingPeriod.ANNUAL);

            final PlanPhase currentPhase = subscription.getCurrentPhase();
            assertNotNull(currentPhase);
            assertEquals(currentPhase.getPhaseType(), PhaseType.DISCOUNT);

            assertListenerStatus();
        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }

    protected void testMultipleChangeLastEOT() throws EntitlementBillingApiException {
        try {
            SubscriptionData subscription = createSubscription("Assault-Rifle", BillingPeriod.ANNUAL, "gunclubDiscount");
            final PlanPhase trialPhase = subscription.getCurrentPhase();
            assertEquals(trialPhase.getPhaseType(), PhaseType.TRIAL);

            testListener.pushExpectedEvent(NextEvent.PHASE);
            Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(31));
            clock.addDeltaFromReality(it.toDurationMillis());
            assertTrue(testListener.isCompleted(5000));

            // SET CTD
            final List<Duration> durationList = new ArrayList<Duration>();
            durationList.add(trialPhase.getDuration());
            final DateTime startDiscountPhase = DefaultClock.addDuration(subscription.getStartDate(), durationList);
            final Duration ctd = getDurationMonth(1);
            final DateTime newChargedThroughDate = DefaultClock.addDuration(startDiscountPhase, ctd);
            billingApi.setChargedThroughDate(subscription.getId(), newChargedThroughDate.toLocalDate(), callContext);
            subscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(subscription.getId(), callContext);

            // CHANGE EOT
            testListener.setNonExpectedMode();
            testListener.pushExpectedEvent(NextEvent.CHANGE);
            subscription.changePlan("Shotgun", BillingPeriod.MONTHLY, "gunclubDiscount", clock.getUTCNow(), callContext);
            assertFalse(testListener.isCompleted(3000));
            testListener.reset();

            // CHANGE EOT
            testListener.setNonExpectedMode();
            testListener.pushExpectedEvent(NextEvent.CHANGE);
            subscription.changePlan("Pistol", BillingPeriod.ANNUAL, "gunclubDiscount", clock.getUTCNow(), callContext);
            assertFalse(testListener.isCompleted(3000));
            testListener.reset();

            // CHECK NO CHANGE OCCURED YET
            Plan currentPlan = subscription.getCurrentPlan();
            assertNotNull(currentPlan);
            assertEquals(currentPlan.getProduct().getName(), "Assault-Rifle");
            assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
            assertEquals(currentPlan.getBillingPeriod(), BillingPeriod.ANNUAL);

            PlanPhase currentPhase = subscription.getCurrentPhase();
            assertNotNull(currentPhase);
            assertEquals(currentPhase.getPhaseType(), PhaseType.DISCOUNT);

            // ACTIVATE CHNAGE BY MOVING AFTER CTD
            testListener.pushExpectedEvent(NextEvent.CHANGE);
            it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(1));
            clock.addDeltaFromReality(it.toDurationMillis());

            assertTrue(testListener.isCompleted(5000));

            currentPlan = subscription.getCurrentPlan();
            assertNotNull(currentPlan);
            assertEquals(currentPlan.getProduct().getName(), "Pistol");
            assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
            assertEquals(currentPlan.getBillingPeriod(), BillingPeriod.ANNUAL);

            currentPhase = subscription.getCurrentPhase();
            assertNotNull(currentPhase);
            assertEquals(currentPhase.getPhaseType(), PhaseType.DISCOUNT);

            // MOVE TO NEXT PHASE
            testListener.pushExpectedEvent(NextEvent.PHASE);
            it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(6));
            clock.addDeltaFromReality(it.toDurationMillis());
            assertTrue(testListener.isCompleted(5000));
            subscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(subscription.getId(), callContext);

            currentPlan = subscription.getCurrentPlan();
            assertNotNull(currentPlan);
            assertEquals(currentPlan.getProduct().getName(), "Pistol");
            assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
            assertEquals(currentPlan.getBillingPeriod(), BillingPeriod.ANNUAL);

            currentPhase = subscription.getCurrentPhase();
            assertNotNull(currentPhase);
            assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);

            assertListenerStatus();
        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }

    protected void testCorrectPhaseAlignmentOnChange() {
        try {
            SubscriptionData subscription = createSubscription("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);
            PlanPhase trialPhase = subscription.getCurrentPhase();
            assertEquals(trialPhase.getPhaseType(), PhaseType.TRIAL);

            // MOVE 2 DAYS AHEAD
            Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(2));
            clock.addDeltaFromReality(it.toDurationMillis());

            // CHANGE IMMEDIATE TO A 3 PHASES PLAN
            testListener.reset();
            testListener.pushExpectedEvent(NextEvent.CHANGE);
            subscription.changePlan("Assault-Rifle", BillingPeriod.ANNUAL, "gunclubDiscount", clock.getUTCNow(), callContext);
            assertTrue(testListener.isCompleted(5000));
            testListener.reset();

            // CHECK EVERYTHING LOOKS CORRECT
            final Plan currentPlan = subscription.getCurrentPlan();
            assertNotNull(currentPlan);
            assertEquals(currentPlan.getProduct().getName(), "Assault-Rifle");
            assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
            assertEquals(currentPlan.getBillingPeriod(), BillingPeriod.ANNUAL);

            trialPhase = subscription.getCurrentPhase();
            assertEquals(trialPhase.getPhaseType(), PhaseType.TRIAL);

            // MOVE AFTER TRIAL PERIOD -> DISCOUNT
            testListener.pushExpectedEvent(NextEvent.PHASE);
            it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(30));
            clock.addDeltaFromReality(it.toDurationMillis());

            assertTrue(testListener.isCompleted(5000));

            trialPhase = subscription.getCurrentPhase();
            assertEquals(trialPhase.getPhaseType(), PhaseType.DISCOUNT);

            subscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(subscription.getId(), callContext);

            final DateTime expectedNextPhaseDate = subscription.getStartDate().plusDays(30).plusMonths(6);
            final EffectiveSubscriptionEvent nextPhase = subscription.getPendingTransition();
            final DateTime nextPhaseEffectiveDate = nextPhase.getEffectiveTransitionTime();

            assertEquals(nextPhaseEffectiveDate, expectedNextPhaseDate);

            assertListenerStatus();

        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }
}
