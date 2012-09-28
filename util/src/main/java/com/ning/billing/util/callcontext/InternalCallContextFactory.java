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

package com.ning.billing.util.callcontext;

import java.util.UUID;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.account.api.Account;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.entity.dao.EntitySqlDao;

public class InternalCallContextFactory {

    private static final Logger log = LoggerFactory.getLogger(InternalCallContextFactory.class);

    // Default tenant id until the multitenancy branch is merged
    private static final long DEFAULT_TENANT_RECORD_ID = 12L;

    //private final EntityDao<Tenant> tenantDao;
    private final EntitySqlDao<Account> accountDao;
    private final Clock clock;

    @Inject
    public InternalCallContextFactory(final Clock clock) {
        // TODO own dao
        this.accountDao = null;
        this.clock = clock;
    }

    // TODO - add tenantId as parameter once the multitenancy branch is merged
    public InternalTenantContext createInternalTenantContext() {
        todoAddAccountId();
        return new InternalTenantContext(DEFAULT_TENANT_RECORD_ID);
    }

    public InternalTenantContext createInternalTenantContext(final TenantContext context) {
        todoAddAccountId();
        return new InternalTenantContext(DEFAULT_TENANT_RECORD_ID);
    }

    // TODO - add tenantId as parameter once the multitenancy branch is merged
    public InternalTenantContext createInternalTenantContext(final UUID accountId) {
        // TODO - remove once the multitenancy branch is merged
        //final Long tenantRecordId = tenantDao.getRecordId(context.getTenantId());
        final Long tenantRecordId = DEFAULT_TENANT_RECORD_ID;
        final Long accountRecordId = tenantRecordId;
        //final Long accountRecordId = accountDao.getRecordId(accountId.toString());

        return new InternalTenantContext(tenantRecordId, accountRecordId);
    }

    public InternalCallContext createInternalCallContext(final String userName, final CallOrigin callOrigin, final UserType userType) {
        return createInternalCallContext(new DefaultCallContext(userName, callOrigin, userType, clock));
    }

    public InternalCallContext createInternalCallContext(final CallContext context) {
        todoAddAccountId();

        // TODO - remove once the multitenancy branch is merged
        //final Long tenantRecordId = tenantDao.getRecordId(context.getTenantId());
        final Long tenantRecordId = DEFAULT_TENANT_RECORD_ID;

        return new InternalCallContext(tenantRecordId, null, context);
    }

    public InternalCallContext createInternalCallContext(final UUID accountId,
                                                         final CallContext context) {
        // TODO - remove once the multitenancy branch is merged
        //final Long tenantRecordId = tenantDao.getRecordId(context.getTenantId());
        final Long tenantRecordId = DEFAULT_TENANT_RECORD_ID;
        final Long accountRecordId = tenantRecordId;
        //final Long accountRecordId = accountDao.getRecordId(accountId.toString());

        return new InternalCallContext(tenantRecordId, accountRecordId, context);
    }

    public InternalTenantContext createInternalTenantContext(final UUID id, final TenantContext context) {
        todoAddAccountId();

        // TODO
        return null;
    }

    private void todoAddAccountId() {
        final StringBuilder builder = new StringBuilder();
        builder.append("TODO FIXME Context: missing accountId!");
        for (final StackTraceElement ste : Thread.currentThread().getStackTrace()) {
            builder.append(ste).append("\n");
        }
        log.warn(builder.toString());
    }
}
