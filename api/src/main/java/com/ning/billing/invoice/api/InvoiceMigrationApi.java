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

package com.ning.billing.invoice.api;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.LocalDate;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.callcontext.CallContext;

public interface InvoiceMigrationApi {

    /**
     * @param accountId  account id
     * @param targetDate maximum billing event day to consider (in the account timezone)
     * @param balance    invoice balance
     * @param currency   invoice currency
     * @param context    call context
     * @return The UUID of the created invoice
     */
    public UUID createMigrationInvoice(UUID accountId,
                                       LocalDate targetDate,
                                       BigDecimal balance,
                                       Currency currency,
                                       CallContext context);
}
