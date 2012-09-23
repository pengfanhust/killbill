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

package com.ning.billing.invoice.api.migration;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;

public class TestDefaultInvoiceMigrationApi extends InvoiceApiTestBase {

    private final Logger log = LoggerFactory.getLogger(TestDefaultInvoiceMigrationApi.class);

    private LocalDate date_migrated;
    private DateTime date_regular;

    private UUID accountId;
    private UUID migrationInvoiceId;
    private UUID regularInvoiceId;

    private static final BigDecimal MIGRATION_INVOICE_AMOUNT = new BigDecimal("100.00");
    private static final Currency MIGRATION_INVOICE_CURRENCY = Currency.USD;

    @BeforeMethod(groups = "slow")
    public void setupMethod() throws Exception {
        date_migrated = clock.getUTCToday().minusYears(1);
        date_regular = clock.getUTCNow();

        final Account account = createAccount();
        accountId = account.getId();
        migrationInvoiceId = createAndCheckMigrationInvoice(accountId);
        regularInvoiceId = generateRegularInvoice(account, date_regular);
    }

    private UUID createAndCheckMigrationInvoice(final UUID accountId) throws InvoiceApiException {
        final UUID migrationInvoiceId = migrationApi.createMigrationInvoice(accountId, date_migrated, MIGRATION_INVOICE_AMOUNT, MIGRATION_INVOICE_CURRENCY);
        Assert.assertNotNull(migrationInvoiceId);
        //Double check it was created and values are correct

        final Invoice invoice = invoiceDao.getById(migrationInvoiceId);
        Assert.assertNotNull(invoice);

        Assert.assertEquals(invoice.getAccountId(), accountId);
        Assert.assertEquals(invoice.getTargetDate().compareTo(date_migrated), 0); //temp to avoid tz test artifact
        //		Assert.assertEquals(invoice.getTargetDate(),now);
        Assert.assertEquals(invoice.getNumberOfItems(), 1);
        Assert.assertEquals(invoice.getInvoiceItems().get(0).getAmount().compareTo(MIGRATION_INVOICE_AMOUNT), 0);
        Assert.assertEquals(invoice.getBalance().compareTo(MIGRATION_INVOICE_AMOUNT), 0);
        Assert.assertEquals(invoice.getCurrency(), MIGRATION_INVOICE_CURRENCY);
        Assert.assertTrue(invoice.isMigrationInvoice());

        return migrationInvoiceId;
    }

    @Test(groups = "slow")
    public void testUserApiAccess() {
        final List<Invoice> byAccount = invoiceUserApi.getInvoicesByAccount(accountId);
        Assert.assertEquals(byAccount.size(), 1);
        Assert.assertEquals(byAccount.get(0).getId(), regularInvoiceId);

        final List<Invoice> byAccountAndDate = invoiceUserApi.getInvoicesByAccount(accountId, date_migrated.minusDays(1));
        Assert.assertEquals(byAccountAndDate.size(), 1);
        Assert.assertEquals(byAccountAndDate.get(0).getId(), regularInvoiceId);

        final Collection<Invoice> unpaid = invoiceUserApi.getUnpaidInvoicesByAccountId(accountId, new LocalDate(date_regular.plusDays(1)));
        Assert.assertEquals(unpaid.size(), 2);
    }

    // Check migration invoice IS returned for payment api calls
    @Test(groups = "slow")
    public void testPaymentApi() {
        final List<Invoice> allByAccount = invoicePaymentApi.getAllInvoicesByAccount(accountId);
        Assert.assertEquals(allByAccount.size(), 2);
        Assert.assertTrue(checkContains(allByAccount, regularInvoiceId));
        Assert.assertTrue(checkContains(allByAccount, migrationInvoiceId));
    }

    // ACCOUNT balance should reflect total of migration and non-migration invoices
    @Test(groups = "slow")
    public void testBalance() throws InvoiceApiException{
        final Invoice migrationInvoice = invoiceDao.getById(migrationInvoiceId);
        final Invoice regularInvoice = invoiceDao.getById(regularInvoiceId);
        final BigDecimal balanceOfAllInvoices = migrationInvoice.getBalance().add(regularInvoice.getBalance());

        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(accountId);
        log.info("ACCOUNT balance: " + accountBalance + " should equal the Balance Of All Invoices: " + balanceOfAllInvoices);
        Assert.assertEquals(accountBalance.compareTo(balanceOfAllInvoices), 0);
    }

    private boolean checkContains(final List<Invoice> invoices, final UUID invoiceId) {
        for (final Invoice invoice : invoices) {
            if (invoice.getId().equals(invoiceId)) {
                return true;
            }
        }
        return false;
    }
}
