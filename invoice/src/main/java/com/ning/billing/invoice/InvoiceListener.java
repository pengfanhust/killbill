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

package com.ning.billing.invoice;

import java.util.UUID;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.events.EffectiveSubscriptionInternalEvent;
import com.ning.billing.util.events.RepairEntitlementInternalEvent;

import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;

public class InvoiceListener {

    private static final Logger log = LoggerFactory.getLogger(InvoiceListener.class);
    private final InvoiceDispatcher dispatcher;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public InvoiceListener(final InternalCallContextFactory internalCallContextFactory, final InvoiceDispatcher dispatcher) {
        this.dispatcher = dispatcher;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Subscribe
    public void handleRepairEntitlementEvent(final RepairEntitlementInternalEvent repairEvent) {
        try {
            final InternalCallContext context = internalCallContextFactory.createInternalCallContext(repairEvent.getTenantRecordId(), repairEvent.getAccountRecordId(), "RepairBundle", CallOrigin.INTERNAL, UserType.SYSTEM, repairEvent.getUserToken());
            dispatcher.processAccount(repairEvent.getAccountId(), repairEvent.getEffectiveDate(), false, context);
        } catch (InvoiceApiException e) {
            log.error(e.getMessage());
        }
    }

    @Subscribe
    public void handleSubscriptionTransition(final EffectiveSubscriptionInternalEvent transition) {
        try {
            //  Skip future uncancel event
            //  Skip events which are marked as not being the last one
            if (transition.getTransitionType() == SubscriptionTransitionType.UNCANCEL ||
                transition.getTransitionType() == SubscriptionTransitionType.MIGRATE_ENTITLEMENT
                || transition.getRemainingEventsForUserOperation() > 0) {
                return;
            }
            final InternalCallContext context = internalCallContextFactory.createInternalCallContext(transition.getTenantRecordId(), transition.getAccountRecordId(), "Transition", CallOrigin.INTERNAL, UserType.SYSTEM, transition.getUserToken());
            dispatcher.processSubscription(transition, context);
        } catch (InvoiceApiException e) {
            log.error(e.getMessage());
        }
    }

    public void handleNextBillingDateEvent(final UUID subscriptionId, final DateTime eventDateTime, final Long accountRecordId, final Long tenantRecordId) {
        try {
            final InternalCallContext context = internalCallContextFactory.createInternalCallContext(tenantRecordId, accountRecordId, "Next Billing Date", CallOrigin.INTERNAL, UserType.SYSTEM, null);
            dispatcher.processSubscription(subscriptionId, eventDateTime, context);
        } catch (InvoiceApiException e) {
            log.error(e.getMessage());
        }
    }
}
