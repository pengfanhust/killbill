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

package com.ning.billing.payment.core;

import static com.ning.billing.payment.glue.PaymentModule.PLUGIN_EXECUTOR_NAMED;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableMap;
import com.google.inject.name.Named;
import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.payment.api.DefaultRefund;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.Refund;
import com.ning.billing.payment.dao.PaymentDao;
import com.ning.billing.payment.dao.PaymentModelDao;
import com.ning.billing.payment.dao.RefundModelDao;
import com.ning.billing.payment.dao.RefundModelDao.RefundStatus;
import com.ning.billing.payment.plugin.api.PaymentPluginApi;
import com.ning.billing.payment.plugin.api.PaymentPluginApiException;
import com.ning.billing.payment.provider.PaymentProviderPluginRegistry;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextFactory;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.globallocker.GlobalLocker;

public class RefundProcessor extends ProcessorBase {

    private static final Logger log = LoggerFactory.getLogger(RefundProcessor.class);

    private final InvoicePaymentApi invoicePaymentApi;
    private final CallContextFactory factory;

    @Inject
    public RefundProcessor(final PaymentProviderPluginRegistry pluginRegistry,
                           final AccountUserApi accountUserApi,
                           final InvoicePaymentApi invoicePaymentApi,
                           final Bus eventBus,
                           final CallContextFactory factory,
                           final TagUserApi tagUserApi,
                           final PaymentDao paymentDao,
                           final GlobalLocker locker,
                           @Named(PLUGIN_EXECUTOR_NAMED) final ExecutorService executor) {
        super(pluginRegistry, accountUserApi, eventBus, paymentDao, tagUserApi, locker, executor);
        this.invoicePaymentApi = invoicePaymentApi;
        this.factory = factory;
    }

    /**
     * Create a refund and adjust the invoice or invoice items as necessary.
     *
     * @param account                   account to refund
     * @param paymentId                 payment associated with that refund
     * @param specifiedRefundAmount     amount to refund. If null, the amount will be the sum of adjusted invoice items
     * @param isAdjusted                whether the refund should trigger an invoice or invoice item adjustment
     * @param invoiceItemIdsWithAmounts invoice item ids and associated amounts to adjust
     * @param context                   the call context
     * @return the created context
     * @throws PaymentApiException
     */
    public Refund createRefund(final Account account, final UUID paymentId, @Nullable final BigDecimal specifiedRefundAmount,
                               final boolean isAdjusted, final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts, final CallContext context)
            throws PaymentApiException {

        return new WithAccountLock<Refund>().processAccountWithLock(locker, account.getExternalKey(), new WithAccountLockCallback<Refund>() {

            @Override
            public Refund doOperation() throws PaymentApiException {
                // First, compute the refund amount, if necessary
                final BigDecimal refundAmount = computeRefundAmount(paymentId, specifiedRefundAmount, invoiceItemIdsWithAmounts);

                try {

                    final PaymentModelDao payment = paymentDao.getPayment(paymentId);
                    if (payment == null) {
                        throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_SUCCESS_PAYMENT, paymentId);
                    }

                    //
                    // We are looking for multiple things:
                    // 1. Compute totalAmountRefunded based on all Refund entries that made it to the plugin.
                    // 2. If we find a CREATED entry (that did not make it to the plugin) with the same amount, we reuse the entry
                    // 3. Compute foundPluginCompletedRefunds, number of refund entries for that amount that made it to the plugin
                    //
                    int foundPluginCompletedRefunds = 0;
                    RefundModelDao refundInfo = null;
                    BigDecimal totalAmountRefunded = BigDecimal.ZERO;
                    final List<RefundModelDao> existingRefunds = paymentDao.getRefundsForPayment(paymentId);
                    for (final RefundModelDao cur : existingRefunds) {

                        final BigDecimal existingPositiveAmount = cur.getAmount();
                        if (existingPositiveAmount.compareTo(refundAmount) == 0) {
                            if (cur.getRefundStatus() == RefundStatus.CREATED) {
                                if (refundInfo == null) {
                                    refundInfo = cur;
                                }
                            } else {
                                foundPluginCompletedRefunds++;
                            }
                        }
                        if (cur.getRefundStatus() != RefundStatus.CREATED) {
                            totalAmountRefunded = totalAmountRefunded.add(existingPositiveAmount);
                        }
                    }

                    if (payment.getAmount().subtract(totalAmountRefunded).compareTo(refundAmount) < 0) {
                        throw new PaymentApiException(ErrorCode.PAYMENT_REFUND_AMOUNT_TOO_LARGE);
                    }

                    if (refundInfo == null) {
                        refundInfo = new RefundModelDao(account.getId(), paymentId, refundAmount, account.getCurrency(), isAdjusted);
                        paymentDao.insertRefund(refundInfo, context);
                    }

                    final PaymentPluginApi plugin = getPaymentProviderPlugin(payment.getPaymentMethodId());
                    final int nbExistingRefunds = plugin.getNbRefundForPaymentAmount(account, paymentId, refundAmount);
                    log.debug(String.format("found %d pluginRefunds for paymentId %s and amount %s", nbExistingRefunds, paymentId, refundAmount));

                    if (nbExistingRefunds > foundPluginCompletedRefunds) {
                        log.info("Found existing plugin refund for paymentId {}, skip plugin", paymentId);
                    } else {
                        // If there is no such existing refund we create it
                        plugin.processRefund(account, paymentId, refundAmount);
                    }
                    paymentDao.updateRefundStatus(refundInfo.getId(), RefundStatus.PLUGIN_COMPLETED, context);

                    invoicePaymentApi.createRefund(paymentId, refundAmount, isAdjusted, invoiceItemIdsWithAmounts, refundInfo.getId(), context);

                    paymentDao.updateRefundStatus(refundInfo.getId(), RefundStatus.COMPLETED, context);

                    return new DefaultRefund(refundInfo.getId(), paymentId, refundInfo.getAmount(), account.getCurrency(),
                                             isAdjusted, refundInfo.getCreatedDate());
                } catch (PaymentPluginApiException e) {
                    throw new PaymentApiException(ErrorCode.PAYMENT_CREATE_REFUND, account.getId(), e.getMessage());
                } catch (InvoiceApiException e) {
                    throw new PaymentApiException(e);
                }
            }
        });
    }

    /**
     * Compute the refund amount (computed from the invoice or invoice items as necessary).
     *
     * @param paymentId                 payment id associated with this refund
     * @param specifiedRefundAmount     amount to refund. If null, the amount will be the sum of adjusted invoice items
     * @param invoiceItemIdsWithAmounts invoice item ids and associated amounts to adjust
     * @return the refund amount
     */
    private BigDecimal computeRefundAmount(final UUID paymentId, @Nullable final BigDecimal specifiedRefundAmount, final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts)
            throws PaymentApiException {
        try {
            final List<InvoiceItem> items = invoicePaymentApi.getInvoiceForPaymentId(paymentId).getInvoiceItems();

            BigDecimal amountFromItems = BigDecimal.ZERO;
            for (final UUID itemId : invoiceItemIdsWithAmounts.keySet()) {
                amountFromItems = amountFromItems.add(Objects.firstNonNull(invoiceItemIdsWithAmounts.get(itemId),
                        getAmountFromItem(items, itemId)));
            }

            // Sanity check: if some items were specified, then the sum should be equal to specified refund amount, if specified
            if (amountFromItems.compareTo(BigDecimal.ZERO) != 0 && specifiedRefundAmount != null && specifiedRefundAmount.compareTo(amountFromItems) != 0) {
                throw new IllegalArgumentException("You can't specify a refund amount that doesn't match the invoice items amounts");
            }

            return Objects.firstNonNull(specifiedRefundAmount, amountFromItems);
        } catch (InvoiceApiException e) {
            throw new PaymentApiException(e);
        }
    }

    private BigDecimal getAmountFromItem(final List<InvoiceItem> items, final UUID itemId) {
        for (final InvoiceItem item : items) {
            if (item.getId().equals(itemId)) {
                return item.getAmount();
            }
        }

        throw new IllegalArgumentException("Unable to find invoice item for id " + itemId);
    }

    public Refund getRefund(final UUID refundId)
            throws PaymentApiException {
        RefundModelDao result = paymentDao.getRefund(refundId);
        if (result == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_REFUND, refundId);
        }
        final List<RefundModelDao> filteredInput = filterUncompletedPluginRefund(Collections.singletonList(result));
        if (filteredInput.size() == 0) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_REFUND, refundId);
        }

        if (completePluginCompletedRefund(filteredInput)) {
            result = paymentDao.getRefund(refundId);
        }
        return new DefaultRefund(result.getId(), result.getPaymentId(), result.getAmount(), result.getCurrency(),
                                 result.isAdjsuted(), result.getCreatedDate());
    }

    public List<Refund> getAccountRefunds(final Account account)
            throws PaymentApiException {
        List<RefundModelDao> result = paymentDao.getRefundsForAccount(account.getId());
        if (completePluginCompletedRefund(result)) {
            result = paymentDao.getRefundsForAccount(account.getId());
        }
        final List<RefundModelDao> filteredInput = filterUncompletedPluginRefund(result);
        return toRefunds(filteredInput);
    }

    public List<Refund> getPaymentRefunds(final UUID paymentId)
            throws PaymentApiException {
        List<RefundModelDao> result = paymentDao.getRefundsForPayment(paymentId);
        if (completePluginCompletedRefund(result)) {
            result = paymentDao.getRefundsForPayment(paymentId);
        }
        final List<RefundModelDao> filteredInput = filterUncompletedPluginRefund(result);
        return toRefunds(filteredInput);
    }

    public List<Refund> toRefunds(final List<RefundModelDao> in) {
        return new ArrayList<Refund>(Collections2.transform(in, new Function<RefundModelDao, Refund>() {
            @Override
            public Refund apply(final RefundModelDao cur) {
                return new DefaultRefund(cur.getId(), cur.getPaymentId(), cur.getAmount(), cur.getCurrency(),
                                         cur.isAdjsuted(), cur.getCreatedDate());
            }
        }));
    }

    private List<RefundModelDao> filterUncompletedPluginRefund(final List<RefundModelDao> input) {
        return new ArrayList<RefundModelDao>(Collections2.filter(input, new Predicate<RefundModelDao>() {
            @Override
            public boolean apply(final RefundModelDao in) {
                return in.getRefundStatus() != RefundStatus.CREATED;
            }
        }));
    }

    private boolean completePluginCompletedRefund(final List<RefundModelDao> refunds) throws PaymentApiException {

        final Collection<RefundModelDao> refundsToBeFixed = Collections2.filter(refunds, new Predicate<RefundModelDao>() {
            @Override
            public boolean apply(final RefundModelDao in) {
                return in.getRefundStatus() == RefundStatus.PLUGIN_COMPLETED;
            }
        });
        if (refundsToBeFixed.size() == 0) {
            return false;
        }

        try {
            final Account account = accountUserApi.getAccountById(refundsToBeFixed.iterator().next().getAccountId());
            new WithAccountLock<Void>().processAccountWithLock(locker, account.getExternalKey(), new WithAccountLockCallback<Void>() {

                @Override
                public Void doOperation() throws PaymentApiException {
                    try {
                        final CallContext context = factory.createCallContext("RefundProcessor", CallOrigin.INTERNAL, UserType.SYSTEM);
                        for (final RefundModelDao cur : refundsToBeFixed) {
                            // TODO - we currently don't save the items to be adjusted. If we crash, they won't be adjusted...
                            invoicePaymentApi.createRefund(cur.getPaymentId(), cur.getAmount(), cur.isAdjsuted(), ImmutableMap.<UUID, BigDecimal>of(), cur.getId(), context);
                            paymentDao.updateRefundStatus(cur.getId(), RefundStatus.COMPLETED, context);
                        }
                    } catch (InvoiceApiException e) {
                        throw new PaymentApiException(e);
                    }
                    return null;
                }
            });
            return true;
        } catch (AccountApiException e) {
            throw new PaymentApiException(e);
        }
    }
}
