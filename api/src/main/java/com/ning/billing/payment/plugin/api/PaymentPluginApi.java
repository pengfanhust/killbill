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

package com.ning.billing.payment.plugin.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.ning.billing.account.api.Account;
import com.ning.billing.payment.api.PaymentMethodPlugin;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;

public interface PaymentPluginApi {

    public String getName();

    public PaymentInfoPlugin processPayment(String externalAccountKey, UUID paymentId, BigDecimal amount, CallContext context)
            throws PaymentPluginApiException;

    public PaymentInfoPlugin getPaymentInfo(UUID paymentId, TenantContext context)
            throws PaymentPluginApiException;

    public void processRefund(final Account account, final UUID paymentId, BigDecimal refundAmount, CallContext context)
            throws PaymentPluginApiException;

    public int getNbRefundForPaymentAmount(final Account account, final UUID paymentId, final BigDecimal refundAmount, TenantContext context)
            throws PaymentPluginApiException;

    public String createPaymentProviderAccount(Account account, CallContext context)
            throws PaymentPluginApiException;

    public List<PaymentMethodPlugin> getPaymentMethodDetails(String accountKey, TenantContext context)
            throws PaymentPluginApiException;

    public PaymentMethodPlugin getPaymentMethodDetail(String accountKey, String externalPaymentMethodId, TenantContext context)
            throws PaymentPluginApiException;

    public String addPaymentMethod(String accountKey, PaymentMethodPlugin paymentMethodProps, boolean setDefault, CallContext context)
            throws PaymentPluginApiException;

    public void updatePaymentMethod(String accountKey, PaymentMethodPlugin paymentMethodProps, CallContext context)
            throws PaymentPluginApiException;

    public void deletePaymentMethod(String accountKey, String externalPaymentMethodId, CallContext context)
            throws PaymentPluginApiException;

    public void setDefaultPaymentMethod(String accountKey, String externalPaymentId, CallContext context)
            throws PaymentPluginApiException;
}
