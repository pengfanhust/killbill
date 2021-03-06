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

import org.skife.jdbi.v2.sqlobject.SqlBatch;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContextBinder;
import com.ning.billing.util.entity.Entity;

public interface CollectionHistorySqlDao<T extends Entity> {

    @SqlBatch
    public void addHistoryFromTransaction(String objectId, ObjectType objectType,
                                          List<EntityHistory<T>> histories,
                                          @InternalTenantContextBinder InternalCallContext context);

    @SqlUpdate
    public void addHistoryFromTransaction(String objectId, ObjectType objectType,
                                          EntityHistory<T> history,
                                          @InternalTenantContextBinder InternalCallContext context);
}
