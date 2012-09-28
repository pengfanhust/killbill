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

package com.ning.billing.junction.api.blocking;

import java.util.List;
import java.util.UUID;

import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.BlockingApi;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.junction.api.DefaultBlockingState;
import com.ning.billing.junction.dao.BlockingStateDao;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.clock.Clock;

import com.google.inject.Inject;

public class DefaultBlockingApi implements BlockingApi {

    private final BlockingStateDao dao;
    private final Clock clock;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultBlockingApi(final BlockingStateDao dao, final Clock clock, final InternalCallContextFactory internalCallContextFactory) {
        this.dao = dao;
        this.clock = clock;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public BlockingState getBlockingStateFor(final Blockable overdueable, final TenantContext context) {
        BlockingState state = dao.getBlockingStateFor(overdueable, internalCallContextFactory.createInternalTenantContext(context));
        if (state == null) {
            state = DefaultBlockingState.getClearState();
        }
        return state;
    }

    @Override
    public BlockingState getBlockingStateFor(final UUID overdueableId, final TenantContext context) {
        return dao.getBlockingStateFor(overdueableId, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public List<BlockingState> getBlockingHistory(final Blockable overdueable, final TenantContext context) {
        return dao.getBlockingHistoryFor(overdueable, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public List<BlockingState> getBlockingHistory(final UUID overdueableId, final TenantContext context) {
        return dao.getBlockingHistoryFor(overdueableId, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public <T extends Blockable> void setBlockingState(final BlockingState state, final CallContext context) {
        dao.setBlockingState(state, clock, internalCallContextFactory.createInternalCallContext(context));
    }
}
