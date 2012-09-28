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

package com.ning.billing.util.entity.collection.dao;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlBatch;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;

import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.InternalTenantContextBinder;
import com.ning.billing.util.dao.Mapper;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.dao.ObjectTypeBinder;
import com.ning.billing.util.dao.RecordIdMapper;
import com.ning.billing.util.entity.Entity;

/**
 * provides consistent semantics for entity collections
 * note: this is intended to be extended by an interface which provides @ExternalizedSqlViaStringTemplate3 and mappers
 *
 * @param <T>
 */
public interface EntityCollectionSqlDao<T extends Entity> {

    @SqlBatch
    public void insertFromTransaction(@Bind("objectId") final String objectId,
                                      @ObjectTypeBinder final ObjectType objectType,
                                      @BindBean final Collection<T> entities,
                                      @InternalTenantContextBinder final InternalCallContext context);

    @SqlBatch
    public void deleteFromTransaction(@Bind("objectId") final String objectId,
                                      @ObjectTypeBinder final ObjectType objectType,
                                      @BindBean final Collection<T> entities,
                                      @InternalTenantContextBinder final InternalCallContext context);

    @SqlQuery
    public List<T> load(@Bind("objectId") final String objectId,
                        @ObjectTypeBinder final ObjectType objectType,
                        @InternalTenantContextBinder final InternalTenantContext context);

    @RegisterMapper(RecordIdMapper.class)
    @SqlQuery
    public List<Mapper<UUID, Long>> getRecordIds(@Bind("objectId") final String objectId,
                                                 @ObjectTypeBinder final ObjectType objectType,
                                                 @InternalTenantContextBinder final InternalTenantContext context);

    @SqlUpdate
    public void test(@InternalTenantContextBinder final InternalTenantContext context);
}
