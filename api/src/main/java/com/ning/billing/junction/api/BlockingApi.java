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

package com.ning.billing.junction.api;

import java.util.List;
import java.util.UUID;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;

public interface BlockingApi {

    public static final String CLEAR_STATE_NAME = "__KILLBILL__CLEAR__OVERDUE_STATE__";

    public BlockingState getBlockingStateFor(Blockable overdueable, TenantContext context);

    public BlockingState getBlockingStateFor(UUID overdueableId, TenantContext context);

    public List<BlockingState> getBlockingHistory(Blockable overdueable, TenantContext context);

    public List<BlockingState> getBlockingHistory(UUID overdueableId, TenantContext context);

    public <T extends Blockable> void setBlockingState(BlockingState state, CallContext context);

}
