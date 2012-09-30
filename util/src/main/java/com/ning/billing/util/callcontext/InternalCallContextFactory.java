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

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.util.clock.Clock;

public class InternalCallContextFactory {

    private static final Logger log = LoggerFactory.getLogger(InternalCallContextFactory.class);

    public static final long INTERNAL_TENANT_RECORD_ID = 0L;

    private final CallContextSqlDao callContextSqlDao;
    private final Clock clock;

    @Inject
    public InternalCallContextFactory(final IDBI dbi, final Clock clock) {
        this.callContextSqlDao = dbi.onDemand(CallContextSqlDao.class);
        this.clock = clock;
    }

    // TODO
    public InternalTenantContext createInternalTenantContext() {
        todoAddAccountId();
        return new InternalTenantContext(INTERNAL_TENANT_RECORD_ID);
    }

    // TODO - remove
    public InternalTenantContext createInternalTenantContext(final TenantContext context) {
        todoAddAccountId();
        return new InternalTenantContext(getTenantRecordId(context));
    }

    public InternalTenantContext createInternalTenantContext(final UUID accountId, final TenantContext context) {
        final Long tenantRecordId = getTenantRecordId(context);
        final Long accountRecordId = getAccountRecordId(accountId);

        return new InternalTenantContext(tenantRecordId, accountRecordId);
    }

    // Internal use only (notification queue, etc.) - no tenant
    public InternalCallContext createInternalCallContext(final String userName, final CallOrigin callOrigin, final UserType userType) {
        return createInternalCallContext(INTERNAL_TENANT_RECORD_ID, null, new DefaultCallContext(userName, callOrigin, userType, clock));
    }

    public InternalCallContext createInternalCallContext(final CallContext context) {
        todoAddAccountId();
        return createInternalCallContext(getTenantRecordId(context), null, context);
    }

    public InternalCallContext createInternalCallContext(final Long tenantRecordId, @Nullable final Long accountRecordId, final CallContext context) {
        return new InternalCallContext(tenantRecordId, accountRecordId, context);
    }

    public InternalCallContext createInternalCallContext(final UUID accountId, final CallContext context) {
        final Long tenantRecordId = getTenantRecordId(context);
        final Long accountRecordId = getAccountRecordId(accountId);

        return new InternalCallContext(tenantRecordId, accountRecordId, context);
    }

    private Long getTenantRecordId(final TenantContext context) {
        // TODO
        if (context.getTenantId() == null) {
            return INTERNAL_TENANT_RECORD_ID;
        } else {
            return callContextSqlDao.getTenantRecordId(context.getTenantId().toString());
        }
    }

    private Long getAccountRecordId(final UUID accountId) {
        return callContextSqlDao.getAccountRecordId(accountId.toString());
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
