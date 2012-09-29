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

package com.ning.billing.payment.provider;

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.ning.billing.account.api.Account;
import com.ning.billing.payment.api.PaymentMethodPlugin;
import com.ning.billing.payment.plugin.api.NoOpPaymentPluginApi;
import com.ning.billing.payment.plugin.api.PaymentInfoPlugin;
import com.ning.billing.payment.plugin.api.PaymentInfoPlugin.PaymentPluginStatus;
import com.ning.billing.payment.plugin.api.PaymentPluginApiException;
import com.ning.billing.payment.plugin.api.PaymentProviderAccount;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.clock.Clock;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;

public class DefaultNoOpPaymentProviderPlugin implements NoOpPaymentPluginApi {

    private static final String PLUGIN_NAME = "__NO_OP__";

    private final AtomicBoolean makeNextInvoiceFailWithError = new AtomicBoolean(false);
    private final AtomicBoolean makeNextInvoiceFailWithException = new AtomicBoolean(false);
    private final AtomicBoolean makeAllInvoicesFailWithError = new AtomicBoolean(false);

    private final Map<UUID, PaymentInfoPlugin> payments = new ConcurrentHashMap<UUID, PaymentInfoPlugin>();
    // Note: we can't use HashMultiMap as we care about storing duplicate key/value pairs
    private final Multimap<UUID, BigDecimal> refunds = LinkedListMultimap.<UUID, BigDecimal>create();
    private final Map<String, List<PaymentMethodPlugin>> paymentMethods = new ConcurrentHashMap<String, List<PaymentMethodPlugin>>();
    private final Map<String, PaymentProviderAccount> accounts = new ConcurrentHashMap<String, PaymentProviderAccount>();

    private final Clock clock;

    @Inject
    public DefaultNoOpPaymentProviderPlugin(final Clock clock) {
        this.clock = clock;
        clear();
    }

    @Override
    public void clear() {
        makeNextInvoiceFailWithException.set(false);
        makeAllInvoicesFailWithError.set(false);
        makeNextInvoiceFailWithError.set(false);
    }

    @Override
    public void makeNextPaymentFailWithError() {
        makeNextInvoiceFailWithError.set(true);
    }

    @Override
    public void makeNextPaymentFailWithException() {
        makeNextInvoiceFailWithException.set(true);
    }

    @Override
    public void makeAllInvoicesFailWithError(final boolean failure) {
        makeAllInvoicesFailWithError.set(failure);
    }

    @Override
    public String getName() {
        return PLUGIN_NAME;
    }

    @Override
    public PaymentInfoPlugin processPayment(final String externalKey, final UUID paymentId, final BigDecimal amount, final CallContext context) throws PaymentPluginApiException {
        if (makeNextInvoiceFailWithException.getAndSet(false)) {
            throw new PaymentPluginApiException("", "test error");
        }

        final PaymentPluginStatus status = (makeAllInvoicesFailWithError.get() || makeNextInvoiceFailWithError.getAndSet(false)) ? PaymentPluginStatus.ERROR : PaymentPluginStatus.PROCESSED;
        final PaymentInfoPlugin result = new DefaultNoOpPaymentInfoPlugin(amount, clock.getUTCNow(), clock.getUTCNow(), status, null);
        payments.put(paymentId, result);
        return result;
    }

    @Override
    public PaymentInfoPlugin getPaymentInfo(final UUID paymentId, final TenantContext context) throws PaymentPluginApiException {
        final PaymentInfoPlugin payment = payments.get(paymentId);
        if (payment == null) {
            throw new PaymentPluginApiException("", "No payment found for id " + paymentId);
        }
        return payment;
    }

    @Override
    public String createPaymentProviderAccount(final Account account, final CallContext context) throws PaymentPluginApiException {
        if (account != null) {
            final String id = UUID.randomUUID().toString();
            final String paymentMethodId = UUID.randomUUID().toString();
            accounts.put(account.getExternalKey(),
                         new PaymentProviderAccount.Builder().setAccountKey(account.getExternalKey())
                                                             .setId(id)
                                                             .setDefaultPaymentMethod(paymentMethodId)
                                                             .build());
            return id;
        } else {
            throw new PaymentPluginApiException("", "Did not get account to create payment provider account");
        }
    }

    @Override
    public String addPaymentMethod(final String accountKey, final PaymentMethodPlugin paymentMethodProps, final boolean setDefault, final CallContext context) throws PaymentPluginApiException {
        final PaymentMethodPlugin realWithID = new DefaultNoOpPaymentMethodPlugin(paymentMethodProps);
        List<PaymentMethodPlugin> pms = paymentMethods.get(accountKey);
        if (pms == null) {
            pms = new LinkedList<PaymentMethodPlugin>();
            paymentMethods.put(accountKey, pms);
        }
        pms.add(realWithID);

        return realWithID.getExternalPaymentMethodId();
    }

    @Override
    public void updatePaymentMethod(final String accountKey, final PaymentMethodPlugin paymentMethodProps, final CallContext context)
            throws PaymentPluginApiException {
        final DefaultNoOpPaymentMethodPlugin e = getPaymentMethod(accountKey, paymentMethodProps.getExternalPaymentMethodId());
        if (e != null) {
            e.setProps(paymentMethodProps.getProperties());
        }
    }

    @Override
    public void deletePaymentMethod(final String accountKey, final String paymentMethodId, final CallContext context) throws PaymentPluginApiException {
        PaymentMethodPlugin toBeDeleted = null;
        final List<PaymentMethodPlugin> pms = paymentMethods.get(accountKey);
        if (pms != null) {
            for (final PaymentMethodPlugin cur : pms) {
                if (cur.getExternalPaymentMethodId().equals(paymentMethodId)) {
                    toBeDeleted = cur;
                    break;
                }
            }
        }

        if (toBeDeleted != null) {
            pms.remove(toBeDeleted);
        }
    }

    @Override
    public List<PaymentMethodPlugin> getPaymentMethodDetails(final String accountKey, final TenantContext context)
            throws PaymentPluginApiException {
        return paymentMethods.get(accountKey);
    }

    @Override
    public PaymentMethodPlugin getPaymentMethodDetail(final String accountKey, final String externalPaymentId, final TenantContext context)
            throws PaymentPluginApiException {
        return getPaymentMethod(accountKey, externalPaymentId);
    }

    @Override
    public void setDefaultPaymentMethod(final String accountKey, final String externalPaymentId, final CallContext context) throws PaymentPluginApiException {
    }

    @Override
    public void processRefund(final Account account, final UUID paymentId, final BigDecimal refundAmount, final CallContext context) throws PaymentPluginApiException {
        final PaymentInfoPlugin paymentInfoPlugin = getPaymentInfo(paymentId, context);
        if (paymentInfoPlugin == null) {
            throw new PaymentPluginApiException("", String.format("No payment found for paymentId %s (plugin %s)", paymentId, getName()));
        }

        BigDecimal maxAmountRefundable = paymentInfoPlugin.getAmount();
        for (final BigDecimal refund : refunds.get(paymentId)) {
            maxAmountRefundable = maxAmountRefundable.add(refund.negate());
        }
        if (maxAmountRefundable.compareTo(refundAmount) < 0) {
            throw new PaymentPluginApiException("", String.format("Refund amount of %s for paymentId %s is bigger than the payment amount %s (plugin %s)",
                                                                  refundAmount, paymentId, paymentInfoPlugin.getAmount(), getName()));
        }

        refunds.put(paymentId, refundAmount);
    }

    @Override
    public int getNbRefundForPaymentAmount(final Account account, final UUID paymentId, final BigDecimal refundAmount, final TenantContext context) throws PaymentPluginApiException {
        int nbRefunds = 0;
        for (final BigDecimal amount : refunds.get(paymentId)) {
            if (amount.compareTo(refundAmount) == 0) {
                nbRefunds++;
            }
        }

        return nbRefunds;
    }

    private DefaultNoOpPaymentMethodPlugin getPaymentMethod(final String accountKey, final String externalPaymentId) {
        final List<PaymentMethodPlugin> pms = paymentMethods.get(accountKey);
        if (pms == null) {
            return null;
        }

        for (final PaymentMethodPlugin cur : pms) {
            if (cur.getExternalPaymentMethodId().equals(externalPaymentId)) {
                return (DefaultNoOpPaymentMethodPlugin) cur;
            }
        }

        return null;
    }
}
