/*
 * Copyright 2010-2012 Ning, Inc.
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
package com.ning.billing.beatrix.integration.overdue;

import java.math.BigDecimal;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.beatrix.integration.BeatrixModule;
import com.ning.billing.beatrix.util.InvoiceChecker.ExpectedItemCheck;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.junction.api.BlockingApi;

import static junit.framework.Assert.assertTrue;

@Test(groups = "slow")
@Guice(modules = {BeatrixModule.class})
public class TestOverdueWithSubscriptionCancellation extends TestOverdueBase {


    @Override
    public String getOverdueConfig() {
        final String configXml = "<overdueConfig>" +
        "   <bundleOverdueStates>" +
           "       <state name=\"OD1\">" +
        "           <condition>" +
        "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
        "                   <unit>DAYS</unit><number>5</number>" +
        "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
        "           </condition>" +
        "           <externalMessage>Reached OD1</externalMessage>" +
        "           <blockChanges>true</blockChanges>" +
        "           <disableEntitlementAndChangesBlocked>false</disableEntitlementAndChangesBlocked>" +
        "           <subscriptionCancellationPolicy>IMMEDIATE</subscriptionCancellationPolicy>" +
        "           <autoReevaluationInterval>" +
        "               <unit>DAYS</unit><number>5</number>" +
        "           </autoReevaluationInterval>" +
        "       </state>" +
        "   </bundleOverdueStates>" +
        "</overdueConfig>";
        return configXml;
    }

    @Test(groups = "slow")
    public void testCheckSubscriptionCancellation() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        // Set next invoice to fail and create subscription
        paymentPlugin.makeAllInvoicesFailWithError(true);
        final Subscription baseSubscription = createSubscriptionAndCheckForCompletion(bundle.getId(), productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.INVOICE);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 5, 1), callContext);

        // DAY 30 have to get out of trial before first payment
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR);

        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 6, 30), callContext);

        // Should still be in clear state
        checkODState(BlockingApi.CLEAR_STATE_NAME);

        // DAY 36 -- RIGHT AFTER OD1
        addDaysAndCheckForCompletion(6, NextEvent.CANCEL);

        // Should be in OD1
        checkODState("OD1");

        final Subscription cancelledBaseSubscription = entitlementUserApi.getSubscriptionFromId(baseSubscription.getId(), callContext);
        assertTrue(cancelledBaseSubscription.getState() == SubscriptionState.CANCELLED);
    }
}
