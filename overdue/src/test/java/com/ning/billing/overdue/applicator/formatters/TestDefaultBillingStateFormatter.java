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

package com.ning.billing.overdue.applicator.formatters;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.overdue.OverdueTestSuite;
import com.ning.billing.overdue.config.api.BillingState;

public class TestDefaultBillingStateFormatter extends OverdueTestSuite {

    @Test(groups = "fast")
    public void testBalanceFormatting() throws Exception {
        final BillingState billingState = new BillingState(UUID.randomUUID(), 2, BigDecimal.TEN,
                                                           new LocalDate(), DateTimeZone.UTC, UUID.randomUUID(),
                                                           null, null);
        final DefaultBillingStateFormatter formatter = new DefaultBillingStateFormatter(billingState);
        Assert.assertEquals(formatter.getFormattedBalanceOfUnpaidInvoices(), "10.00");
    }
}
