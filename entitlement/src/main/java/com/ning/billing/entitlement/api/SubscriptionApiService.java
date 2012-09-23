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

package com.ning.billing.entitlement.api;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.ActionPolicy;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.util.callcontext.CallContext;

public interface SubscriptionApiService {

    public SubscriptionData createPlan(SubscriptionBuilder builder, Plan plan, PhaseType initialPhase,
                                       String realPriceList, DateTime requestedDate, DateTime effectiveDate, DateTime processedDate,
                                       CallContext context)
            throws EntitlementUserApiException;

    public boolean recreatePlan(SubscriptionData subscription, PlanPhaseSpecifier spec, DateTime requestedDate, CallContext context)
            throws EntitlementUserApiException;

    public boolean cancel(SubscriptionData subscription, DateTime requestedDate, CallContext context)
        throws EntitlementUserApiException;

    public boolean cancelWithPolicy(SubscriptionData subscription, DateTime requestedDate, ActionPolicy policy, CallContext context)
        throws EntitlementUserApiException;

    public boolean uncancel(SubscriptionData subscription, CallContext context)
            throws EntitlementUserApiException;

    public boolean changePlan(SubscriptionData subscription, String productName, BillingPeriod term,
                              String priceList, DateTime requestedDate, CallContext context)
            throws EntitlementUserApiException;

    public boolean changePlanWithPolicy(SubscriptionData subscription, String productName, BillingPeriod term,
                                        String priceList, DateTime requestedDate, ActionPolicy policy, CallContext context)
            throws EntitlementUserApiException;
}
