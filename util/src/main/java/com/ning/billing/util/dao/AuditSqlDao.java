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

package com.ning.billing.util.dao;

import java.util.List;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlBatch;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Define;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;

import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.InternalTenantContextBinder;

@ExternalizedSqlViaStringTemplate3
@RegisterMapper(AuditLogMapper.class)
public interface AuditSqlDao {

    @SqlUpdate
    public void insertAuditFromTransaction(@AuditBinder final EntityAudit audit,
                                           @InternalTenantContextBinder final InternalCallContext context);

    @SqlBatch(transactional = false)
    public void insertAuditFromTransaction(@AuditBinder final List<EntityAudit> audit,
                                           @InternalTenantContextBinder final InternalCallContext context);

    @SqlQuery
    public List<AuditLog> getAuditLogsForRecordId(@TableNameBinder final TableName tableName,
                                                  @Bind("recordId") final long recordId,
                                                  @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    public Long getRecordId(@Bind("id") final String id, @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    public Long getRecordIdForTable(@Define("tableName") final String tableName,
                                    @Bind("id") final String id,
                                    @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    public List<Long> getHistoryRecordIdsForTable(@Define("tableName") final String tableName,
                                                  @Bind("id") final String id,
                                                  @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    public Long getHistoryRecordId(@Bind("recordId") final Long recordId,
                                   @InternalTenantContextBinder final InternalTenantContext context);
}
