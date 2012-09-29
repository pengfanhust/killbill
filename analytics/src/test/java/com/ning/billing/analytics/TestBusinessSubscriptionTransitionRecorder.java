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

package com.ning.billing.analytics;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.analytics.dao.BusinessSubscriptionTransitionSqlDao;
import com.ning.billing.analytics.model.BusinessSubscriptionTransition;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.user.EffectiveSubscriptionEvent;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.clock.DefaultClock;

import com.google.common.collect.ImmutableList;

public class TestBusinessSubscriptionTransitionRecorder extends AnalyticsTestSuite {

    @Test(groups = "fast")
    public void testCreateAddOn() throws Exception {
        final UUID bundleId = UUID.randomUUID();
        final UUID externalKey = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();
        final UUID subscriptionId = UUID.randomUUID();

        // Setup the catalog
        final CatalogService catalogService = Mockito.mock(CatalogService.class);
        Mockito.when(catalogService.getFullCatalog()).thenReturn(Mockito.mock(Catalog.class));

        // Setup the dao
        final BusinessSubscriptionTransitionSqlDao sqlDao = new MockBusinessSubscriptionTransitionSqlDao();

        // Setup the entitlement API
        final SubscriptionBundle bundle = Mockito.mock(SubscriptionBundle.class);
        Mockito.when(bundle.getId()).thenReturn(bundleId);
        Mockito.when(bundle.getAccountId()).thenReturn(accountId);
        Mockito.when(bundle.getKey()).thenReturn(externalKey.toString());
        final EntitlementUserApi entitlementApi = Mockito.mock(EntitlementUserApi.class);
        Mockito.when(entitlementApi.getBundleFromId(Mockito.<UUID>any(), Mockito.<TenantContext>any())).thenReturn(bundle);

        // Setup the account API
        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getExternalKey()).thenReturn(externalKey.toString());
        final AccountUserApi accountApi = Mockito.mock(AccountUserApi.class);
        Mockito.when(accountApi.getAccountById(Mockito.eq(bundle.getAccountId()), Mockito.<TenantContext>any())).thenReturn(account);

        // Create an new subscription event
        final EffectiveSubscriptionEvent eventEffective = Mockito.mock(EffectiveSubscriptionEvent.class);
        Mockito.when(eventEffective.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(eventEffective.getTransitionType()).thenReturn(SubscriptionTransitionType.CREATE);
        Mockito.when(eventEffective.getSubscriptionId()).thenReturn(subscriptionId);
        Mockito.when(eventEffective.getRequestedTransitionTime()).thenReturn(new DateTime(DateTimeZone.UTC));
        Mockito.when(eventEffective.getNextPlan()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(eventEffective.getEffectiveTransitionTime()).thenReturn(new DateTime(DateTimeZone.UTC));
        Mockito.when(eventEffective.getSubscriptionStartDate()).thenReturn(new DateTime(DateTimeZone.UTC));

        final Subscription subscription = Mockito.mock(Subscription.class);
        Mockito.when(subscription.getId()).thenReturn(subscriptionId);
        Mockito.when(subscription.getAllTransitions()).thenReturn(ImmutableList.<EffectiveSubscriptionEvent>of(eventEffective));
        Mockito.when(entitlementApi.getSubscriptionsForBundle(Mockito.<UUID>any(), Mockito.<TenantContext>any())).thenReturn(ImmutableList.<Subscription>of(subscription));

        final BusinessSubscriptionTransitionRecorder recorder = new BusinessSubscriptionTransitionRecorder(sqlDao, catalogService, entitlementApi, accountApi, new DefaultClock());
        recorder.rebuildTransitionsForBundle(bundle.getId(), internalCallContext);

        Assert.assertEquals(sqlDao.getTransitionsByKey(externalKey.toString(), internalCallContext).size(), 1);
        final BusinessSubscriptionTransition transition = sqlDao.getTransitionsByKey(externalKey.toString(), internalCallContext).get(0);
        Assert.assertEquals(transition.getTotalOrdering(), (long) eventEffective.getTotalOrdering());
        Assert.assertEquals(transition.getAccountKey(), externalKey.toString());
        // Make sure all the prev_ columns are null
        Assert.assertNull(transition.getPreviousSubscription());
    }
}
