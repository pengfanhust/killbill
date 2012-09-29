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

package com.ning.billing.payment.core;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.config.PaymentConfig;
import com.ning.billing.payment.PaymentTestSuite;
import com.ning.billing.payment.api.PaymentMethod;
import com.ning.billing.payment.dao.MockPaymentDao;
import com.ning.billing.payment.provider.DefaultPaymentProviderPluginRegistry;
import com.ning.billing.payment.provider.ExternalPaymentProviderPlugin;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.globallocker.GlobalLocker;

public class TestPaymentMethodProcessor extends PaymentTestSuite {

    private PaymentMethodProcessor processor;

    @BeforeMethod(groups = "fast")
    public void setUp() throws Exception {
        final DefaultPaymentProviderPluginRegistry pluginRegistry = new DefaultPaymentProviderPluginRegistry(Mockito.mock(PaymentConfig.class));
        pluginRegistry.register(new ExternalPaymentProviderPlugin(new ClockMock()), ExternalPaymentProviderPlugin.PLUGIN_NAME);

        final AccountUserApi accountUserApi = Mockito.mock(AccountUserApi.class);
        final Bus bus = Mockito.mock(Bus.class);
        final MockPaymentDao paymentDao = new MockPaymentDao();
        final GlobalLocker globalLocker = Mockito.mock(GlobalLocker.class);
        final ExecutorService executorService = Mockito.mock(ExecutorService.class);
        final TagUserApi tagUserApi =  Mockito.mock(TagUserApi.class);
        processor = new PaymentMethodProcessor(pluginRegistry, accountUserApi, bus, paymentDao, tagUserApi, globalLocker, executorService);
    }

    @Test(groups = "fast")
    public void testGetExternalPaymentProviderPlugin() throws Exception {
        final UUID accountId = UUID.randomUUID();
        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getId()).thenReturn(accountId);
        Mockito.when(account.getExternalKey()).thenReturn(accountId.toString());

        Assert.assertEquals(processor.getPaymentMethods(account, false, internalCallContext).size(), 0);

        // The first call should create the payment method
        final ExternalPaymentProviderPlugin providerPlugin = processor.getExternalPaymentProviderPlugin(account, internalCallContext);
        Assert.assertEquals(providerPlugin.getName(), ExternalPaymentProviderPlugin.PLUGIN_NAME);
        final List<PaymentMethod> paymentMethods = processor.getPaymentMethods(account, false, internalCallContext);
        Assert.assertEquals(paymentMethods.size(), 1);
        Assert.assertEquals(paymentMethods.get(0).getPluginName(), ExternalPaymentProviderPlugin.PLUGIN_NAME);
        Assert.assertEquals(paymentMethods.get(0).getAccountId(), account.getId());

        // The succeeding calls should not create any other payment method
        final UUID externalPaymentMethodId = paymentMethods.get(0).getId();
        for (int i = 0; i < 50; i++) {
            final ExternalPaymentProviderPlugin foundProviderPlugin = processor.getExternalPaymentProviderPlugin(account, internalCallContext);
            Assert.assertEquals(foundProviderPlugin.getName(), ExternalPaymentProviderPlugin.PLUGIN_NAME);

            final List<PaymentMethod> foundPaymentMethods = processor.getPaymentMethods(account, false, internalCallContext);
            Assert.assertEquals(foundPaymentMethods.size(), 1);
            Assert.assertEquals(foundPaymentMethods.get(0).getPluginName(), ExternalPaymentProviderPlugin.PLUGIN_NAME);
            Assert.assertEquals(foundPaymentMethods.get(0).getAccountId(), account.getId());
            Assert.assertEquals(foundPaymentMethods.get(0).getId(), externalPaymentMethodId);
        }
    }
}
