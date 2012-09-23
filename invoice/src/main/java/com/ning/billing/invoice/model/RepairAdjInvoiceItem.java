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

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoiceItemType;

public class RepairAdjInvoiceItem extends AdjInvoiceItem {

    public RepairAdjInvoiceItem(final UUID invoiceId, final UUID accountId, final LocalDate startDate, final LocalDate endDate,
                                final BigDecimal amount, final Currency currency, final UUID reversingId) {
        super(invoiceId, accountId, startDate, endDate, amount, currency, reversingId);
    }

    public RepairAdjInvoiceItem(final UUID id, final UUID invoiceId, final UUID accountId, final LocalDate startDate, final LocalDate endDate,
                                final BigDecimal amount, final Currency currency, final UUID reversingId) {
        super(id, invoiceId, accountId, startDate, endDate, amount, currency, reversingId);
    }

    @Override
    public InvoiceItemType getInvoiceItemType() {
        return InvoiceItemType.REPAIR_ADJ;
    }

    @Override
    public String getDescription() {
        return "Adjustment (entitlement change)";
    }
}
