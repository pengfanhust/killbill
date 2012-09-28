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

import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.entitlement.alignment.PlanAligner;
import com.ning.billing.entitlement.api.SubscriptionApiService;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionApiService;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.glue.DefaultEntitlementModule;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.clock.Clock;

import com.google.inject.Inject;
import com.google.inject.name.Named;

public class RepairSubscriptionApiService extends DefaultSubscriptionApiService implements SubscriptionApiService {

    @Inject
    public RepairSubscriptionApiService(final Clock clock,
                                        @Named(DefaultEntitlementModule.REPAIR_NAMED) final EntitlementDao dao,
                                        final CatalogService catalogService,
                                        final PlanAligner planAligner,
                                        final InternalCallContextFactory internalCallContextFactory) {
        super(clock, dao, catalogService, planAligner, internalCallContextFactory);
    }
}
