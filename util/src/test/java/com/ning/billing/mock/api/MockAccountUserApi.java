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

package com.ning.billing.mock.api;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.joda.time.DateTimeZone;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.AccountEmail;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.account.api.BillCycleDay;
import com.ning.billing.account.api.MigrationAccountData;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.mock.MockAccountBuilder;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;

public class MockAccountUserApi implements AccountUserApi {

    private final CopyOnWriteArrayList<Account> accounts = new CopyOnWriteArrayList<Account>();

    public Account createAccount(final UUID id,
                                 final String externalKey,
                                 final String email,
                                 final String name,
                                 final int firstNameLength,
                                 final Currency currency,
                                 final BillCycleDay billCycleDay,
                                 final UUID paymentMethodId,
                                 final DateTimeZone timeZone,
                                 final String locale,
                                 final String address1,
                                 final String address2,
                                 final String companyName,
                                 final String city,
                                 final String stateOrProvince,
                                 final String country,
                                 final String postalCode,
                                 final String phone) {

        final Account result = new MockAccountBuilder(id)
                .externalKey(externalKey)
                .email(email)
                .name(name).firstNameLength(firstNameLength)
                .currency(currency)
                .billingCycleDay(billCycleDay)
                .paymentMethodId(paymentMethodId)
                .timeZone(timeZone)
                .locale(locale)
                .address1(address1)
                .address2(address2)
                .companyName(companyName)
                .city(city)
                .stateOrProvince(stateOrProvince)
                .country(country)
                .postalCode(postalCode)
                .phone(phone)
                .isNotifiedForInvoices(false)
                .build();
        accounts.add(result);
        return result;
    }

    @Override
    public Account createAccount(final AccountData data, final CallContext context) throws AccountApiException {
        final Account result = new MockAccountBuilder(data).build();
        accounts.add(result);
        return result;
    }

    @Override
    public Account getAccountByKey(final String key, final TenantContext context) {
        for (final Account account : accounts) {
            if (key.equals(account.getExternalKey())) {
                return account;
            }
        }
        return null;
    }

    @Override
    public Account getAccountById(final UUID uid, final TenantContext context) {
        for (final Account account : accounts) {
            if (uid.equals(account.getId())) {
                return account;
            }
        }
        return null;
    }

    @Override
    public List<Account> getAccounts(final TenantContext context) {
        return new ArrayList<Account>(accounts);
    }

    @Override
    public UUID getIdFromKey(final String externalKey, final TenantContext context) {
        for (final Account account : accounts) {
            if (externalKey.equals(account.getExternalKey())) {
                return account.getId();
            }
        }
        return null;
    }

    @Override
    public List<AccountEmail> getEmails(final UUID accountId, final TenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void saveEmails(final UUID accountId, final List<AccountEmail> emails, final CallContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addEmail(final UUID accountId, final AccountEmail email, final CallContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeEmail(final UUID accountId, final AccountEmail email, final CallContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateAccount(final Account account, final CallContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Account migrateAccount(final MigrationAccountData data, final CallContext context)
            throws AccountApiException {
        final Account result = new MockAccountBuilder(data).build();
        accounts.add(result);
        return result;
    }

    @Override
    public void updateAccount(final String key, final AccountData accountData, final CallContext context)
            throws AccountApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateAccount(final UUID accountId, final AccountData accountData, final CallContext context)
            throws AccountApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updatePaymentMethod(final UUID accountId, final UUID paymentMethodId, final CallContext context) throws AccountApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removePaymentMethod(final UUID accountId, final CallContext context) throws AccountApiException {
        throw new UnsupportedOperationException();
    }
}
