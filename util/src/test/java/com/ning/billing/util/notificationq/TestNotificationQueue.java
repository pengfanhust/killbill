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

package com.ning.billing.util.notificationq;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.ning.billing.KillbillTestSuiteWithEmbeddedDB;
import com.ning.billing.config.NotificationConfig;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.util.UtilTestSuiteWithEmbeddedDB;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.io.IOUtils;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueHandler;
import com.ning.billing.util.notificationq.dao.NotificationSqlDao;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.name.Names;

import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.testng.Assert.assertEquals;

@Guice(modules = TestNotificationQueue.TestNotificationQueueModule.class)
public class TestNotificationQueue extends UtilTestSuiteWithEmbeddedDB {
    private final Logger log = LoggerFactory.getLogger(TestNotificationQueue.class);

    private static final UUID accountId = UUID.randomUUID();

    @Inject
    private IDBI dbi;

    @Inject
    MysqlTestingHelper helper;

    @Inject
    private Clock clock;

    private DummySqlTest dao;

    private int eventsReceived;

    private static final class TestNotificationKey implements NotificationKey, Comparable<TestNotificationKey> {
        private final String value;

        @JsonCreator
        public TestNotificationKey(@JsonProperty("value") final String value) {
            super();
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public int compareTo(TestNotificationKey arg0) {
            return value.compareTo(arg0.value);
        }
    }

    @BeforeSuite(groups = "slow")
    public void setup() throws Exception {
        final String testDdl = IOUtils.toString(NotificationSqlDao.class.getResourceAsStream("/com/ning/billing/util/ddl_test.sql"));
        helper.initDb(testDdl);
        dao = dbi.onDemand(DummySqlTest.class);
    }

    @BeforeTest(groups = "slow")
    public void beforeTest() {
        dbi.withHandle(new HandleCallback<Void>() {

            @Override
            public Void withHandle(final Handle handle) throws Exception {
                handle.execute("delete from notifications");
                handle.execute("delete from claimed_notifications");
                handle.execute("delete from dummy");
                return null;
            }
        });
        // Reset time to real value
        ((ClockMock) clock).resetDeltaFromReality();
        eventsReceived = 0;
    }

    /**
     * Test that we can post a notification in the future from a transaction and get the notification
     * callback with the correct key when the time is ready
     *
     * @throws Exception
     */
    @Test(groups = "slow")
    public void testSimpleNotification() throws Exception {

        final Map<NotificationKey, Boolean> expectedNotifications = new TreeMap<NotificationKey, Boolean>();

        final DefaultNotificationQueue queue = new DefaultNotificationQueue(dbi, clock, "test-svc", "foo",
                                                                            new NotificationQueueHandler() {
                                                                                @Override
                                                                                public void handleReadyNotification(final NotificationKey notificationKey, final DateTime eventDateTime) {
                                                                                    synchronized (expectedNotifications) {
                                                                                        log.info("Handler received key: " + notificationKey);

                                                                                        expectedNotifications.put(notificationKey, Boolean.TRUE);
                                                                                        expectedNotifications.notify();
                                                                                    }
                                                                                }
                                                                            },
                                                                            getNotificationConfig(false, 100, 1, 10000),
                                                                            new InternalCallContextFactory(clock));

        queue.startQueue();

        final UUID key = UUID.randomUUID();
        final DummyObject obj = new DummyObject("foo", key);
        final DateTime now = new DateTime();
        final DateTime readyTime = now.plusMillis(2000);
        final NotificationKey notificationKey = new TestNotificationKey(key.toString());

        expectedNotifications.put(notificationKey, Boolean.FALSE);

        // Insert dummy to be processed in 2 sec'
        dao.inTransaction(new Transaction<Void, DummySqlTest>() {
            @Override
            public Void inTransaction(final DummySqlTest transactional,
                                      final TransactionStatus status) throws Exception {

                transactional.insertDummy(obj);
                queue.recordFutureNotificationFromTransaction(transactional, readyTime, accountId, notificationKey, internalCallContext);
                log.info("Posted key: " + notificationKey);

                return null;
            }
        });

        // Move time in the future after the notification effectiveDate
        ((ClockMock) clock).setDeltaFromReality(3000);

        // Notification should have kicked but give it at least a sec' for thread scheduling
        await().atMost(1, MINUTES).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return expectedNotifications.get(notificationKey);
            }
        });

        queue.stopQueue();
        Assert.assertTrue(expectedNotifications.get(notificationKey));
    }

    @Test(groups = "slow")
    public void testManyNotifications() throws InterruptedException {
        final Map<NotificationKey, Boolean> expectedNotifications = new TreeMap<NotificationKey, Boolean>();

        final DefaultNotificationQueue queue = new DefaultNotificationQueue(dbi, clock, "test-svc", "many",
                                                                            new NotificationQueueHandler() {
                                                                                @Override
                                                                                public void handleReadyNotification(final NotificationKey notificationKey, final DateTime eventDateTime) {
                                                                                    synchronized (expectedNotifications) {
                                                                                        expectedNotifications.put(notificationKey, Boolean.TRUE);
                                                                                        expectedNotifications.notify();
                                                                                    }
                                                                                }
                                                                            },
                                                                            getNotificationConfig(false, 100, 10, 10000),
                                                                            new InternalCallContextFactory(clock));

        queue.startQueue();

        final DateTime now = clock.getUTCNow();
        final int MAX_NOTIFICATIONS = 100;
        for (int i = 0; i < MAX_NOTIFICATIONS; i++) {

            final int nextReadyTimeIncrementMs = 1000;

            final UUID key = UUID.randomUUID();
            final DummyObject obj = new DummyObject("foo", key);
            final int currentIteration = i;

            final NotificationKey notificationKey = new TestNotificationKey(key.toString());
            expectedNotifications.put(notificationKey, Boolean.FALSE);

            dao.inTransaction(new Transaction<Void, DummySqlTest>() {
                @Override
                public Void inTransaction(final DummySqlTest transactional,
                                          final TransactionStatus status) throws Exception {

                    transactional.insertDummy(obj);
                    queue.recordFutureNotificationFromTransaction(transactional, now.plus((currentIteration + 1) * nextReadyTimeIncrementMs),
                                                                  accountId, notificationKey, internalCallContext);
                    return null;
                }
            });

            // Move time in the future after the notification effectiveDate
            if (i == 0) {
                ((ClockMock) clock).setDeltaFromReality(nextReadyTimeIncrementMs);
            } else {
                ((ClockMock) clock).addDeltaFromReality(nextReadyTimeIncrementMs);
            }
        }

        // Wait a little longer since there are a lot of callback that need to happen
        int nbTry = MAX_NOTIFICATIONS + 1;
        boolean success = false;
        do {
            synchronized (expectedNotifications) {
                final Collection<Boolean> completed = Collections2.filter(expectedNotifications.values(), new Predicate<Boolean>() {
                    @Override
                    public boolean apply(final Boolean input) {
                        return input;
                    }
                });

                if (completed.size() == MAX_NOTIFICATIONS) {
                    success = true;
                    break;
                }
                //log.debug(String.format("BEFORE WAIT : Got %d notifications at time %s (real time %s)", completed.size(), clock.getUTCNow(), new DateTime()));
                expectedNotifications.wait(1000);
            }
        } while (nbTry-- > 0);

        queue.stopQueue();
        log.info("STEPH GOT SIZE " + Collections2.filter(expectedNotifications.values(), new Predicate<Boolean>() {
            @Override
            public boolean apply(final Boolean input) {
                return input;
            }
        }).size());
        assertEquals(success, true);
    }

    /**
     * Test that we can post a notification in the future from a transaction and get the notification
     * callback with the correct key when the time is ready
     *
     * @throws Exception
     */
    @Test(groups = "slow")
    public void testMultipleHandlerNotification() throws Exception {
        final Map<NotificationKey, Boolean> expectedNotificationsFred = new TreeMap<NotificationKey, Boolean>();
        final Map<NotificationKey, Boolean> expectedNotificationsBarney = new TreeMap<NotificationKey, Boolean>();

        final NotificationQueueService notificationQueueService = new DefaultNotificationQueueService(dbi, clock, new InternalCallContextFactory(clock));

        final NotificationConfig config = new NotificationConfig() {
            @Override
            public boolean isNotificationProcessingOff() {
                return false;
            }

            @Override
            public long getSleepTimeMs() {
                return 10;
            }
        };

        final NotificationQueue queueFred = notificationQueueService.createNotificationQueue("UtilTest", "Fred", new NotificationQueueHandler() {
            @Override
            public void handleReadyNotification(final NotificationKey notificationKey, final DateTime eventDateTime) {
                log.info("Fred received key: " + notificationKey);
                expectedNotificationsFred.put(notificationKey, Boolean.TRUE);
                eventsReceived++;
            }
        },
                                                                                             config);

        final NotificationQueue queueBarney = notificationQueueService.createNotificationQueue("UtilTest", "Barney", new NotificationQueueHandler() {
            @Override
            public void handleReadyNotification(final NotificationKey notificationKey, final DateTime eventDateTime) {
                log.info("Barney received key: " + notificationKey);
                expectedNotificationsBarney.put(notificationKey, Boolean.TRUE);
                eventsReceived++;
            }
        },
                                                                                               config);

        queueFred.startQueue();
        //		We don't start Barney so it can never pick up notifications

        final UUID key = UUID.randomUUID();
        final DummyObject obj = new DummyObject("foo", key);
        final DateTime now = new DateTime();
        final DateTime readyTime = now.plusMillis(2000);
        final NotificationKey notificationKeyFred = new TestNotificationKey("Fred");

        final NotificationKey notificationKeyBarney = new TestNotificationKey("Barney");

        expectedNotificationsFred.put(notificationKeyFred, Boolean.FALSE);
        expectedNotificationsFred.put(notificationKeyBarney, Boolean.FALSE);

        // Insert dummy to be processed in 2 sec'
        dao.inTransaction(new Transaction<Void, DummySqlTest>() {
            @Override
            public Void inTransaction(final DummySqlTest transactional,
                                      final TransactionStatus status) throws Exception {

                transactional.insertDummy(obj);
                queueFred.recordFutureNotificationFromTransaction(transactional, readyTime, accountId, notificationKeyFred, internalCallContext);
                log.info("posted key: " + notificationKeyFred.toString());
                queueBarney.recordFutureNotificationFromTransaction(transactional, readyTime, accountId, notificationKeyBarney, internalCallContext);
                log.info("posted key: " + notificationKeyBarney.toString());

                return null;
            }
        });

        // Move time in the future after the notification effectiveDate
        ((ClockMock) clock).setDeltaFromReality(3000);

        // Note the timeout is short on this test, but expected behaviour is that it times out.
        // We are checking that the Fred queue does not pick up the Barney event
        try {
            await().atMost(5, TimeUnit.SECONDS).until(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return eventsReceived >= 2;
                }
            });
            Assert.fail("There should only have been one event for the queue to pick up - it got more than that");
        } catch (Exception e) {
            // expected behavior
        }

        queueFred.stopQueue();
        Assert.assertTrue(expectedNotificationsFred.get(notificationKeyFred));
        Assert.assertFalse(expectedNotificationsFred.get(notificationKeyBarney));
    }

    NotificationConfig getNotificationConfig(final boolean off, final long sleepTime, final int maxReadyEvents, final long claimTimeMs) {
        return new NotificationConfig() {
            @Override
            public boolean isNotificationProcessingOff() {
                return off;
            }

            @Override
            public long getSleepTimeMs() {
                return sleepTime;
            }
        };
    }

    @Test(groups = "slow")
    public void testRemoveNotifications() throws InterruptedException {
        final UUID key = UUID.randomUUID();
        final NotificationKey notificationKey = new TestNotificationKey(key.toString());
        final UUID key2 = UUID.randomUUID();
        final NotificationKey notificationKey2 = new TestNotificationKey(key2.toString());

        final DefaultNotificationQueue queue = new DefaultNotificationQueue(dbi, clock, "test-svc", "many",
                                                                            new NotificationQueueHandler() {
                                                                                @Override
                                                                                public void handleReadyNotification(final NotificationKey inputKey, final DateTime eventDateTime) {
                                                                                    if (inputKey.equals(notificationKey) || inputKey.equals(notificationKey2)) { //ignore stray events from other tests
                                                                                        log.info("Received notification with key: " + notificationKey);
                                                                                        eventsReceived++;
                                                                                    }
                                                                                }
                                                                            },
                                                                            getNotificationConfig(false, 100, 10, 10000),
                                                                            new InternalCallContextFactory(clock));

        queue.startQueue();

        final DateTime start = clock.getUTCNow().plusHours(1);
        final int nextReadyTimeIncrementMs = 1000;

        // add 3 events

        dao.inTransaction(new Transaction<Void, DummySqlTest>() {
            @Override
            public Void inTransaction(final DummySqlTest transactional,
                                      final TransactionStatus status) throws Exception {

                queue.recordFutureNotificationFromTransaction(transactional, start.plus(nextReadyTimeIncrementMs), accountId, notificationKey, internalCallContext);
                queue.recordFutureNotificationFromTransaction(transactional, start.plus(2 * nextReadyTimeIncrementMs), accountId, notificationKey, internalCallContext);
                queue.recordFutureNotificationFromTransaction(transactional, start.plus(3 * nextReadyTimeIncrementMs), accountId, notificationKey2, internalCallContext);
                return null;
            }
        });

        queue.removeNotificationsByKey(notificationKey, internalCallContext); // should remove 2 of the 3

        // Move time in the future after the notification effectiveDate
        ((ClockMock) clock).setDeltaFromReality(4000000 + nextReadyTimeIncrementMs * 3);

        try {
            await().atMost(10, TimeUnit.SECONDS).until(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return eventsReceived >= 2;
                }
            });
            Assert.fail("There should only have been only one event left in the queue we got: " + eventsReceived);
        } catch (Exception e) {
            // expected behavior
        }
        log.info("Received " + eventsReceived + " events");
        queue.stopQueue();
    }

    public static class TestNotificationQueueModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(Clock.class).to(ClockMock.class);

            final MysqlTestingHelper helper = KillbillTestSuiteWithEmbeddedDB.getMysqlTestingHelper();
            bind(MysqlTestingHelper.class).toInstance(helper);
            final IDBI dbi = helper.getDBI();
            bind(IDBI.class).toInstance(dbi);
            final IDBI otherDbi = helper.getDBI();
            bind(IDBI.class).annotatedWith(Names.named("global-lock")).toInstance(otherDbi);
        }
    }
}
