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

import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.UUID;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.entitlement.api.billing.BillingEvent;
import com.ning.billing.entitlement.api.billing.EntitlementBillingApi;
import com.ning.billing.entitlement.api.user.SubscriptionTransition;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.invoice.model.BillingEventSet;
import com.ning.billing.invoice.model.InvoiceGenerator;
import com.ning.billing.invoice.model.InvoiceItemList;
import com.ning.billing.util.globallocker.GlobalLock;
import com.ning.billing.util.globallocker.GlobalLocker;
import com.ning.billing.util.globallocker.LockFailedException;
import com.ning.billing.util.globallocker.GlobalLocker.LockerService;

public class InvoiceDispatcher {
	private final static Logger log = LoggerFactory.getLogger(InvoiceDispatcher.class);
    private final static int NB_LOCK_TRY = 5;

    private final InvoiceGenerator generator;
    private final EntitlementBillingApi entitlementBillingApi;
    private final AccountUserApi accountUserApi;
    private final InvoiceDao invoiceDao;
    private final GlobalLocker locker;

    private final static boolean VERBOSE_OUTPUT = false;
    @Inject
    public InvoiceDispatcher(final InvoiceGenerator generator, final AccountUserApi accountUserApi,
                           final EntitlementBillingApi entitlementBillingApi,
                           final InvoiceDao invoiceDao,
                           final GlobalLocker locker) {
        this.generator = generator;
        this.entitlementBillingApi = entitlementBillingApi;
        this.accountUserApi = accountUserApi;
        this.invoiceDao = invoiceDao;
        this.locker = locker;
    }


    public void processSubscription(final SubscriptionTransition transition) throws InvoiceApiException {
        UUID subscriptionId = transition.getSubscriptionId();
        DateTime targetDate = transition.getEffectiveTransitionTime();
        log.info("Got subscription transition from InvoiceListener. id: " + subscriptionId.toString() + "; targetDate: " + targetDate.toString());
        log.info("Transition type: " + transition.getTransitionType().toString());
        processSubscription(subscriptionId, targetDate);
    }

    public void processSubscription(final UUID subscriptionId, final DateTime targetDate) throws InvoiceApiException {
        if (subscriptionId == null) {
            log.error("Failed handling entitlement change.", new InvoiceApiException(ErrorCode.INVOICE_INVALID_TRANSITION));
            return;
        }

        UUID accountId = entitlementBillingApi.getAccountIdFromSubscriptionId(subscriptionId);
        if (accountId == null) {
            log.error("Failed handling entitlement change.",
                    new InvoiceApiException(ErrorCode.INVOICE_NO_ACCOUNT_ID_FOR_SUBSCRIPTION_ID, subscriptionId.toString()));
            return;
        }
        processAccount(accountId, targetDate, false);
    }
    
    public Invoice processAccount(UUID accountId, DateTime targetDate, boolean dryrun) throws InvoiceApiException {
		GlobalLock lock = null;
        try {
            lock = locker.lockWithNumberOfTries(LockerService.INVOICE, accountId.toString(), NB_LOCK_TRY);

            return processAccountWithLock(accountId, targetDate, dryrun);

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

    private Invoice processAccountWithLock(final UUID accountId, final DateTime targetDate, boolean dryrun) throws InvoiceApiException {

        Account account = accountUserApi.getAccountById(accountId);
        if (account == null) {
            log.error("Failed handling entitlement change.",
                    new InvoiceApiException(ErrorCode.INVOICE_ACCOUNT_ID_INVALID, accountId.toString()));
            return null;
        }

        SortedSet<BillingEvent> events = entitlementBillingApi.getBillingEventsForAccount(accountId);
        BillingEventSet billingEvents = new BillingEventSet(events);

        Currency targetCurrency = account.getCurrency();

        List<InvoiceItem> items = invoiceDao.getInvoiceItemsByAccount(accountId);
        InvoiceItemList invoiceItemList = new InvoiceItemList(items);
        Invoice invoice = generator.generateInvoice(accountId, billingEvents, invoiceItemList, targetDate, targetCurrency);

        if (invoice == null) {
            log.info("Generated null invoice.");
            outputDebugData(events, invoiceItemList);
        } else {
            log.info("Generated invoice {} with {} items.", invoice.getId().toString(), invoice.getNumberOfItems());

            if (VERBOSE_OUTPUT) {
                log.info("New items");
                for (InvoiceItem item : invoice.getInvoiceItems()) {
                    log.info(item.toString());
                }
            }
            outputDebugData(events, invoiceItemList);

            if (invoice.getNumberOfItems() > 0 && !dryrun) {
                invoiceDao.create(invoice);
            }
        }
        
        return invoice;
    }

    private void outputDebugData(Collection<BillingEvent> events, Collection<InvoiceItem> invoiceItemList) {
        if (VERBOSE_OUTPUT) {
            log.info("Events");
            for (BillingEvent event : events) {
                log.info(event.toString());
            }

            log.info("Existing items");
            for (InvoiceItem item : invoiceItemList) {
                log.info(item.toString());
            }
        }
    }
}