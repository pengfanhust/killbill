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

package com.ning.billing.tenant.api.user;

import java.util.UUID;

import com.ning.billing.ErrorCode;
import com.ning.billing.tenant.api.DefaultTenant;
import com.ning.billing.tenant.api.Tenant;
import com.ning.billing.tenant.api.TenantApiException;
import com.ning.billing.tenant.api.TenantData;
import com.ning.billing.tenant.api.TenantUserApi;
import com.ning.billing.tenant.dao.TenantDao;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.entity.EntityPersistenceException;

import com.google.inject.Inject;

public class DefaultTenantUserApi implements TenantUserApi {

    private final TenantDao tenantDao;

    @Inject
    public DefaultTenantUserApi(final TenantDao tenantDao) {
        this.tenantDao = tenantDao;
    }

    @Override
    public Tenant createTenant(final TenantData data, final CallContext context) throws TenantApiException {
        final Tenant tenant = new DefaultTenant(data);

        try {
            tenantDao.create(tenant, context);
        } catch (EntityPersistenceException e) {
            throw new TenantApiException(e, ErrorCode.TENANT_CREATION_FAILED);
        }

        return tenant;
    }

    @Override
    public Tenant getTenantByApiKey(final String key) throws TenantApiException {
        final Tenant tenant = tenantDao.getTenantByApiKey(key);
        if (tenant == null) {
            throw new TenantApiException(ErrorCode.TENANT_DOES_NOT_EXIST_FOR_API_KEY, key);
        }
        return tenant;
    }

    @Override
    public Tenant getTenantById(final UUID id) throws TenantApiException {
        final Tenant tenant = tenantDao.getById(id);
        if (tenant == null) {
            throw new TenantApiException(ErrorCode.TENANT_DOES_NOT_EXIST_FOR_ID, id);
        }
        return tenant;
    }
}
