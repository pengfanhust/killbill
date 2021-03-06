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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.BillCycleDay;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceNotifier;
import com.ning.billing.invoice.api.user.DefaultInvoiceCreationEvent;
import com.ning.billing.invoice.api.user.DefaultNullInvoiceEvent;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.invoice.generator.InvoiceDateUtils;
import com.ning.billing.invoice.generator.InvoiceGenerator;
import com.ning.billing.invoice.model.FixedPriceInvoiceItem;
import com.ning.billing.invoice.model.RecurringInvoiceItem;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.events.BusInternalEvent;
import com.ning.billing.util.events.EffectiveSubscriptionInternalEvent;
import com.ning.billing.util.events.InvoiceCreationInternalEvent;
import com.ning.billing.util.globallocker.GlobalLock;
import com.ning.billing.util.globallocker.GlobalLocker;
import com.ning.billing.util.globallocker.GlobalLocker.LockerType;
import com.ning.billing.util.globallocker.LockFailedException;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;
import com.ning.billing.util.svcapi.junction.BillingEventSet;
import com.ning.billing.util.svcapi.junction.BillingInternalApi;
import com.ning.billing.util.svcsapi.bus.InternalBus;
import com.ning.billing.util.svcsapi.bus.InternalBus.EventBusException;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.inject.Inject;

public class InvoiceDispatcher {

    private static final Logger log = LoggerFactory.getLogger(InvoiceDispatcher.class);
    private static final int NB_LOCK_TRY = 5;

    private final InvoiceGenerator generator;
    private final BillingInternalApi billingApi;
    private final AccountInternalApi accountApi;
    private final EntitlementInternalApi entitlementApi;
    private final InvoiceDao invoiceDao;
    private final InvoiceNotifier invoiceNotifier;
    private final GlobalLocker locker;
    private final InternalBus eventBus;
    private final Clock clock;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public InvoiceDispatcher(final InvoiceGenerator generator, final AccountInternalApi accountApi,
                             final BillingInternalApi billingApi,
                             final EntitlementInternalApi entitlementApi,
                             final InvoiceDao invoiceDao,
                             final InvoiceNotifier invoiceNotifier,
                             final GlobalLocker locker,
                             final InternalBus eventBus,
                             final Clock clock,
                             final InternalCallContextFactory internalCallContextFactory) {
        this.generator = generator;
        this.billingApi = billingApi;
        this.entitlementApi = entitlementApi;
        this.accountApi = accountApi;
        this.invoiceDao = invoiceDao;
        this.invoiceNotifier = invoiceNotifier;
        this.locker = locker;
        this.eventBus = eventBus;
        this.clock = clock;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    public void processSubscription(final EffectiveSubscriptionInternalEvent transition,
                                    final InternalCallContext context) throws InvoiceApiException {
        final UUID subscriptionId = transition.getSubscriptionId();
        final DateTime targetDate = transition.getEffectiveTransitionTime();
        log.info("Got subscription transition: type: " + transition.getTransitionType().toString() + "; id: " + subscriptionId.toString() + "; targetDate: " + targetDate.toString());
        processSubscription(subscriptionId, targetDate, context);
    }

    public void processSubscription(final UUID subscriptionId, final DateTime targetDate, final InternalCallContext context) throws InvoiceApiException {
        try {
            if (subscriptionId == null) {
                log.error("Failed handling entitlement change.", new InvoiceApiException(ErrorCode.INVOICE_INVALID_TRANSITION));
                return;
            }
            final UUID accountId = entitlementApi.getAccountIdFromSubscriptionId(subscriptionId, context);
            processAccount(accountId, targetDate, false, context);
        } catch (EntitlementUserApiException e) {
            log.error("Failed handling entitlement change.",
                      new InvoiceApiException(ErrorCode.INVOICE_NO_ACCOUNT_ID_FOR_SUBSCRIPTION_ID, subscriptionId.toString()));
        }
    }

    public Invoice processAccount(final UUID accountId, final DateTime targetDate,
                                  final boolean dryRun, final InternalCallContext context) throws InvoiceApiException {
        GlobalLock lock = null;
        try {
            lock = locker.lockWithNumberOfTries(LockerType.ACCOUNT_FOR_INVOICE_PAYMENTS, accountId.toString(), NB_LOCK_TRY);

            return processAccountWithLock(accountId, targetDate, dryRun, context);
        } catch (LockFailedException e) {
            // Not good!
            log.error(String.format("Failed to process invoice for account %s, targetDate %s",
                                    accountId.toString(), targetDate), e);
        } finally {
            if (lock != null) {
                lock.release();
            }
        }
        return null;
    }

    private Invoice processAccountWithLock(final UUID accountId, final DateTime targetDateTime,
                                           final boolean dryRun, final InternalCallContext context) throws InvoiceApiException {
        try {

            // Make sure to first set the BCD if needed then get the account object (to have the BCD set)
            final BillingEventSet billingEvents = billingApi.getBillingEventsForAccountAndUpdateAccountBCD(accountId, context);

            final Account account = accountApi.getAccountById(accountId,  context);
            List<Invoice> invoices = new ArrayList<Invoice>();
            if (!billingEvents.isAccountAutoInvoiceOff()) {
                invoices = invoiceDao.getInvoicesByAccount(accountId, context); //no need to fetch, invoicing is off on this account
            }

            final Currency targetCurrency = account.getCurrency();

            // All the computations in invoice are performed on days, in the account timezone
            final LocalDate targetDate = new LocalDate(targetDateTime, account.getTimeZone());

            final Invoice invoice = generator.generateInvoice(accountId, billingEvents, invoices, targetDate, account.getTimeZone(), targetCurrency);
            if (invoice == null) {
                log.info("Generated null invoice.");
                if (!dryRun) {
                    final BusInternalEvent event = new DefaultNullInvoiceEvent(accountId, clock.getUTCToday(), context.getUserToken(),
                            context.getAccountRecordId(), context.getTenantRecordId());
                    postEvent(event, accountId, context);
                }
            } else {
                log.info("Generated invoice {} with {} items.", invoice.getId().toString(), invoice.getNumberOfItems());
                if (!dryRun) {
                    // We need to check whether this is just a 'shell' invoice or a real invoice with items on it
                    final boolean isRealInvoiceWithItems = Collections2.filter(invoice.getInvoiceItems(), new Predicate<InvoiceItem>() {
                        @Override
                        public boolean apply(InvoiceItem input) {
                            return input.getInvoiceId().equals(invoice.getId());
                        }
                    }).size() > 0;

                    invoiceDao.create(invoice, account.getBillCycleDay().getDayOfMonthUTC(), isRealInvoiceWithItems, context);

                    final List<InvoiceItem> fixedPriceInvoiceItems = invoice.getInvoiceItems(FixedPriceInvoiceItem.class);
                    final List<InvoiceItem> recurringInvoiceItems = invoice.getInvoiceItems(RecurringInvoiceItem.class);
                    setChargedThroughDates(account.getBillCycleDay(), account.getTimeZone(), fixedPriceInvoiceItems, recurringInvoiceItems, context);

                    final InvoiceCreationInternalEvent event = new DefaultInvoiceCreationEvent(invoice.getId(), invoice.getAccountId(),
                                                                                       invoice.getBalance(), invoice.getCurrency(),
                                                                                       context.getUserToken(),
                                                                                       context.getAccountRecordId(),
                                                                                       context.getTenantRecordId());

                    if (isRealInvoiceWithItems) {
                        postEvent(event, accountId, context);
                    }
                }
            }

            if (account.isNotifiedForInvoices() && invoice != null && !dryRun) {
                // Need to re-hydrate the invoice object to get the invoice number (record id)
                // API_FIX InvoiceNotifier public API?
                invoiceNotifier.notify(account, invoiceDao.getById(invoice.getId(), context), context.toTenantContext());
            }

            return invoice;
        } catch (AccountApiException e) {
            log.error("Failed handling entitlement change.", e);
            return null;
        }
    }

    private void setChargedThroughDates(final BillCycleDay billCycleDay,
                                        final DateTimeZone accountTimeZone,
                                        final Collection<InvoiceItem> fixedPriceItems,
                                        final Collection<InvoiceItem> recurringItems,
                                        final InternalCallContext context) {
        final Map<UUID, LocalDate> chargeThroughDates = new HashMap<UUID, LocalDate>();
        addInvoiceItemsToChargeThroughDates(billCycleDay, accountTimeZone, chargeThroughDates, fixedPriceItems);
        addInvoiceItemsToChargeThroughDates(billCycleDay, accountTimeZone, chargeThroughDates, recurringItems);

        for (final UUID subscriptionId : chargeThroughDates.keySet()) {
            if (subscriptionId != null) {
                final LocalDate chargeThroughDate = chargeThroughDates.get(subscriptionId);
                log.info("Setting CTD for subscription {} to {}", subscriptionId.toString(), chargeThroughDate.toString());
                entitlementApi.setChargedThroughDate(subscriptionId, chargeThroughDate, context);
            }
        }
    }

    private void postEvent(final BusInternalEvent event, final UUID accountId, final InternalCallContext context) {
        try {
            eventBus.post(event, context);
        } catch (EventBusException e) {
            log.error(String.format("Failed to post event %s for account %s", event.getBusEventType(), accountId), e);
        }
    }

    private void addInvoiceItemsToChargeThroughDates(final BillCycleDay billCycleDay,
                                                     final DateTimeZone accountTimeZone,
                                                     final Map<UUID, LocalDate> chargeThroughDates,
                                                     final Collection<InvoiceItem> items) {

        for (final InvoiceItem item : items) {
            final UUID subscriptionId = item.getSubscriptionId();
            final LocalDate endDate = (item.getEndDate() != null) ? item.getEndDate() : item.getStartDate();

            if (chargeThroughDates.containsKey(subscriptionId)) {
                if (chargeThroughDates.get(subscriptionId).isBefore(endDate)) {
                    // The CTD should always align with the BCD
                    final LocalDate ctd = InvoiceDateUtils.calculateBillingCycleDateOnOrAfter(endDate, accountTimeZone, billCycleDay.getDayOfMonthLocal());
                    chargeThroughDates.put(subscriptionId, ctd);
                }
            } else {
                chargeThroughDates.put(subscriptionId, endDate);
            }
        }
    }
}
