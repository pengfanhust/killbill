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

package com.ning.billing.payment.bus;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.core.PaymentProcessor;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.DefaultCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.api.ControlTagDeletionEvent;

import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;

public class TagHandler {

    private static final Logger log = LoggerFactory.getLogger(TagHandler.class);

    private final Clock clock;
    private final AccountUserApi accountUserApi;
    private final PaymentProcessor paymentProcessor;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public TagHandler(final Clock clock,
                      final AccountUserApi accountUserApi,
                      final PaymentProcessor paymentProcessor,
                      final InternalCallContextFactory internalCallContextFactory) {
        this.clock = clock;
        this.accountUserApi = accountUserApi;
        this.paymentProcessor = paymentProcessor;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Subscribe
    public void process_AUTO_PAY_OFF_removal(final ControlTagDeletionEvent event) {
        if (event.getTagDefinition().getName().equals(ControlTagType.AUTO_PAY_OFF.toString()) && event.getObjectType() == ObjectType.ACCOUNT) {
            final UUID accountId = event.getObjectId();
            processUnpaid_AUTO_PAY_OFF_payments(accountId, event.getUserToken());
        }
    }

    private void processUnpaid_AUTO_PAY_OFF_payments(final UUID accountId, final UUID userToken) {
        try {
            final CallContext context = new DefaultCallContext("PaymentRequestProcessor", CallOrigin.INTERNAL, UserType.SYSTEM, userToken, clock);
            final Account account = accountUserApi.getAccountById(accountId, context);

            paymentProcessor.process_AUTO_PAY_OFF_removal(account, internalCallContextFactory.createInternalCallContext(accountId, context));

        } catch (AccountApiException e) {
            log.warn(String.format("Failed to process process  removal AUTO_PAY_OFF for account %s", accountId), e);
        } catch (PaymentApiException e) {
            log.warn(String.format("Failed to process process  removal AUTO_PAY_OFF for account %s", accountId), e);
        }
    }
}
