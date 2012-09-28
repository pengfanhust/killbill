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

package com.ning.billing.invoice.notification;

import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.joda.time.DateTime;
import org.mockito.Mockito;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.ning.billing.KillbillTestSuiteWithEmbeddedDB;
import com.ning.billing.catalog.MockCatalogModule;
import com.ning.billing.config.InvoiceConfig;
import com.ning.billing.dbi.DBIProvider;
import com.ning.billing.dbi.DbiConfig;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.invoice.InvoiceDispatcher;
import com.ning.billing.invoice.InvoiceListener;
import com.ning.billing.invoice.InvoiceTestSuiteWithEmbeddedDB;
import com.ning.billing.invoice.glue.InvoiceModuleWithMocks;
import com.ning.billing.lifecycle.KillbillService;
import com.ning.billing.mock.glue.MockClockModule;
import com.ning.billing.mock.glue.MockJunctionModule;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.callcontext.CallContextFactory;
import com.ning.billing.util.callcontext.DefaultCallContextFactory;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.email.templates.TemplateModule;
import com.ning.billing.util.glue.BusModule;
import com.ning.billing.util.glue.BusModule.BusType;
import com.ning.billing.util.glue.NotificationQueueModule;
import com.ning.billing.util.glue.TagStoreModule;
import com.ning.billing.util.notificationq.DummySqlTest;
import com.ning.billing.util.notificationq.NotificationQueueService;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;

import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.MINUTES;

public class TestNextBillingDateNotifier extends InvoiceTestSuiteWithEmbeddedDB {
    private Clock clock;
    private DefaultNextBillingDateNotifier notifier;
    private DummySqlTest dao;
    private Bus eventBus;
    private InvoiceListenerMock listener;
    private NotificationQueueService notificationQueueService;

    private static final class InvoiceListenerMock extends InvoiceListener {
        int eventCount = 0;
        UUID latestSubscriptionId = null;

        public InvoiceListenerMock(final CallContextFactory factory, final InvoiceDispatcher dispatcher) {
            super(factory, dispatcher);
        }

        @Override
        public void handleNextBillingDateEvent(final UUID subscriptionId,
                                               final DateTime eventDateTime) {
            eventCount++;
            latestSubscriptionId = subscriptionId;
        }

        public int getEventCount() {
            return eventCount;
        }

        public UUID getLatestSubscriptionId() {
            return latestSubscriptionId;
        }

    }

    @BeforeClass(groups = {"slow"})
    public void setup() throws KillbillService.ServiceException, IOException, ClassNotFoundException, SQLException, EntitlementUserApiException {
        //TestApiBase.loadSystemPropertiesFromClasspath("/entitlement.properties");
        final Injector g = Guice.createInjector(Stage.PRODUCTION, new AbstractModule() {
            @Override
            protected void configure() {
                install(new MockClockModule());
                install(new BusModule(BusType.MEMORY));
                install(new InvoiceModuleWithMocks());
                install(new MockJunctionModule());
                install(new MockCatalogModule());
                install(new NotificationQueueModule());
                install(new TemplateModule());
                install(new TagStoreModule());

                final MysqlTestingHelper helper = KillbillTestSuiteWithEmbeddedDB.getMysqlTestingHelper();
                bind(MysqlTestingHelper.class).toInstance(helper);
                if (helper.isUsingLocalInstance()) {
                    bind(IDBI.class).toProvider(DBIProvider.class).asEagerSingleton();
                    final DbiConfig config = new ConfigurationObjectFactory(System.getProperties()).build(DbiConfig.class);
                    bind(DbiConfig.class).toInstance(config);
                } else {
                    final IDBI dbi = helper.getDBI();
                    bind(IDBI.class).toInstance(dbi);
                }
            }
        });

        clock = g.getInstance(Clock.class);
        final IDBI dbi = g.getInstance(IDBI.class);
        dao = dbi.onDemand(DummySqlTest.class);
        eventBus = g.getInstance(Bus.class);
        notificationQueueService = g.getInstance(NotificationQueueService.class);
        final InvoiceDispatcher dispatcher = g.getInstance(InvoiceDispatcher.class);

        final Subscription subscription = Mockito.mock(Subscription.class);
        final EntitlementUserApi entitlementUserApi = Mockito.mock(EntitlementUserApi.class);
        Mockito.when(entitlementUserApi.getSubscriptionFromId(Mockito.<UUID>any(), Mockito.<TenantContext>any())).thenReturn(subscription);

        final CallContextFactory factory = new DefaultCallContextFactory(clock);
        listener = new InvoiceListenerMock(factory, dispatcher);
        notifier = new DefaultNextBillingDateNotifier(notificationQueueService, g.getInstance(InvoiceConfig.class), entitlementUserApi,
                                                      listener, new InternalCallContextFactory(clock));
    }

    @Test(groups = "slow")
    public void testInvoiceNotifier() throws Exception {

        final UUID accountId = UUID.randomUUID();
        final UUID subscriptionId = new UUID(0L, 1L);
        final DateTime now = new DateTime();
        final DateTime readyTime = now.plusMillis(2000);
        final NextBillingDatePoster poster = new DefaultNextBillingDatePoster(notificationQueueService, new InternalCallContextFactory(clock));

        eventBus.start();
        notifier.initialize();
        notifier.start();

        dao.inTransaction(new Transaction<Void, DummySqlTest>() {
            @Override
            public Void inTransaction(final DummySqlTest transactional,
                                      final TransactionStatus status) throws Exception {

                poster.insertNextBillingNotification(transactional, accountId, subscriptionId, readyTime);
                return null;
            }
        });

        // Move time in the future after the notification effectiveDate
        ((ClockMock) clock).setDeltaFromReality(3000);

        await().atMost(1, MINUTES).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return listener.getEventCount() == 1;
            }
        });

        Assert.assertEquals(listener.getEventCount(), 1);
        Assert.assertEquals(listener.getLatestSubscriptionId(), subscriptionId);
    }

    @AfterClass(groups = "slow")
    public void tearDown() throws Exception {
        notifier.stop();
    }
}
