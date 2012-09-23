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

package com.ning.billing.invoice.model;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;

public class RecurringInvoiceItem extends InvoiceItemBase {
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormat.mediumDate();

    public RecurringInvoiceItem(final UUID invoiceId, final UUID accountId, final UUID bundleId, final UUID subscriptionId,
                                final String planName, final String phaseName, final LocalDate startDate, final LocalDate endDate,
                                final BigDecimal amount, final BigDecimal rate, final Currency currency) {
        super(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount, rate, currency);
    }

    public RecurringInvoiceItem(final UUID invoiceId, final UUID accountId, final UUID bundleId, final UUID subscriptionId, final String planName, final String phaseName,
                                final LocalDate startDate, final LocalDate endDate, final BigDecimal amount, final BigDecimal rate,
                                final Currency currency, final UUID reversedItemId) {
        super(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount, rate, currency);
    }

    public RecurringInvoiceItem(final UUID id, final UUID invoiceId, final UUID accountId, final UUID bundleId, final UUID subscriptionId,
                                final String planName, final String phaseName, final LocalDate startDate, final LocalDate endDate,
                                final BigDecimal amount, final BigDecimal rate, final Currency currency) {
        super(id, invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount, rate, currency);
    }

    @Override
    public String getDescription() {
        return phaseName;
    }

    @Override
    public UUID getLinkedItemId() {
        return linkedItemId;
    }

    public boolean reversesItem() {
        return (linkedItemId != null);
    }

    @Override
    public BigDecimal getRate() {
        return rate;
    }

    @Override
    public int compareTo(final InvoiceItem item) {
        if (item == null) {
            return -1;
        }
        if (!(item instanceof RecurringInvoiceItem)) {
            return -1;
        }

        final RecurringInvoiceItem that = (RecurringInvoiceItem) item;
        final int compareAccounts = getAccountId().compareTo(that.getAccountId());
        if (compareAccounts == 0 && bundleId != null) {
            final int compareBundles = getBundleId().compareTo(that.getBundleId());
            if (compareBundles == 0 && subscriptionId != null) {

                final int compareSubscriptions = getSubscriptionId().compareTo(that.getSubscriptionId());
                if (compareSubscriptions == 0) {
                    final int compareStartDates = getStartDate().compareTo(that.getStartDate());
                    if (compareStartDates == 0) {
                        return getEndDate().compareTo(that.getEndDate());
                    } else {
                        return compareStartDates;
                    }
                } else {
                    return compareSubscriptions;
                }
            } else {
                return compareBundles;
            }
        } else {
            return compareAccounts;
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final RecurringInvoiceItem that = (RecurringInvoiceItem) o;

        // do not include invoice item type, since a reversing item can be equal to the original item
        if (accountId.compareTo(that.accountId) != 0) {
            return false;
        }
        if (amount.compareTo(that.amount) != 0) {
            return false;
        }
        if (currency != that.currency) {
            return false;
        }
        if (startDate.compareTo(that.startDate) != 0) {
            return false;
        }
        if (endDate.compareTo(that.endDate) != 0) {
            return false;
        }
        if (!phaseName.equals(that.phaseName)) {
            return false;
        }
        if (!planName.equals(that.planName)) {
            return false;
        }
        if (rate.compareTo(that.rate) != 0) {
            return false;
        }
        if (linkedItemId != null ? !linkedItemId.equals(that.linkedItemId) : that.linkedItemId != null) {
            return false;
        }
        if (subscriptionId != null ? !subscriptionId.equals(that.subscriptionId) : that.subscriptionId != null) {
            return false;
        }
        if (bundleId != null ? !bundleId.equals(that.bundleId) : that.bundleId != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = accountId.hashCode();
        result = 31 * result + (subscriptionId != null ? subscriptionId.hashCode() : 0);
        result = 31 * result + (bundleId != null ? bundleId.hashCode() : 0);
        result = 31 * result + planName.hashCode();
        result = 31 * result + phaseName.hashCode();
        result = 31 * result + startDate.hashCode();
        result = 31 * result + endDate.hashCode();
        result = 31 * result + amount.hashCode();
        result = 31 * result + rate.hashCode();
        result = 31 * result + currency.hashCode();
        result = 31 * result + getInvoiceItemType().hashCode();
        result = 31 * result + (linkedItemId != null ? linkedItemId.hashCode() : 0);
        return result;
    }

    @Override
    public InvoiceItemType getInvoiceItemType() {
        return InvoiceItemType.RECURRING;
    }
}
