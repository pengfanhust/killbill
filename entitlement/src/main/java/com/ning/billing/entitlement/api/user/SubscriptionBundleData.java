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

package com.ning.billing.entitlement.api.user;

import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.overdue.OverdueState;

public class SubscriptionBundleData implements SubscriptionBundle {
    private final UUID id;
    private final String key;
    private final UUID accountId;
    private final DateTime lastSysTimeUpdate;
    private final OverdueState<SubscriptionBundle> overdueState;

    public SubscriptionBundleData(final String name, final UUID accountId, final DateTime startDate) {
        this(UUID.randomUUID(), name, accountId, startDate);
    }

    public SubscriptionBundleData(final UUID id, final String key, final UUID accountId, final DateTime lastSysUpdate) {
        this(id, key, accountId, lastSysUpdate, null);
    }

    public SubscriptionBundleData(final UUID id, final String key, final UUID accountId, final DateTime lastSysUpdate, @Nullable final OverdueState<SubscriptionBundle> overdueState) {
        this.id = id;
        this.key = key;
        this.accountId = accountId;
        this.lastSysTimeUpdate = lastSysUpdate;
        this.overdueState = overdueState;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    public DateTime getLastSysUpdateTime() {
        return lastSysTimeUpdate;
    }

    @Override
    public OverdueState<SubscriptionBundle> getOverdueState() {
        return overdueState;
    }

    @Override
    public BlockingState getBlockingState() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("SubscriptionBundleData");
        sb.append("{accountId=").append(accountId);
        sb.append(", id=").append(id);
        sb.append(", key='").append(key).append('\'');
        sb.append(", lastSysTimeUpdate=").append(lastSysTimeUpdate);
        sb.append(", overdueState=").append(overdueState);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final SubscriptionBundleData that = (SubscriptionBundleData) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (id != null ? !id.equals(that.id) : that.id != null) {
            return false;
        }
        if (key != null ? !key.equals(that.key) : that.key != null) {
            return false;
        }
        if (lastSysTimeUpdate != null ? !lastSysTimeUpdate.equals(that.lastSysTimeUpdate) : that.lastSysTimeUpdate != null) {
            return false;
        }
        if (overdueState != null ? !overdueState.equals(that.overdueState) : that.overdueState != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (key != null ? key.hashCode() : 0);
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (lastSysTimeUpdate != null ? lastSysTimeUpdate.hashCode() : 0);
        result = 31 * result + (overdueState != null ? overdueState.hashCode() : 0);
        return result;
    }
}
