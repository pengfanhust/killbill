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

package com.ning.billing.jaxrs.json;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.ning.billing.entitlement.api.timeline.BundleTimeline;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline;
import com.ning.billing.util.audit.AuditLog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class BundleJsonWithSubscriptions extends BundleJsonSimple {

    private final List<SubscriptionJsonWithEvents> subscriptions;

    @JsonCreator
    public BundleJsonWithSubscriptions(@JsonProperty("bundleId") @Nullable final String bundleId,
                                       @JsonProperty("externalKey") @Nullable final String externalKey,
                                       @JsonProperty("subscriptions") @Nullable final List<SubscriptionJsonWithEvents> subscriptions,
                                       @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(bundleId, externalKey, auditLogs);
        this.subscriptions = subscriptions;
    }

    @JsonProperty("subscriptions")
    public List<SubscriptionJsonWithEvents> getSubscriptions() {
        return subscriptions;
    }

    public BundleJsonWithSubscriptions(final BundleTimeline bundle, final List<AuditLog> auditLogs,
                                       final Map<UUID, List<AuditLog>> subscriptionsAuditLogs, final Map<UUID, List<AuditLog>> subscriptionEventsAuditLogs) {
        super(bundle.getBundleId(), bundle.getExternalKey(), auditLogs);
        this.subscriptions = new LinkedList<SubscriptionJsonWithEvents>();
        for (final SubscriptionTimeline subscriptionTimeline : bundle.getSubscriptions()) {
            this.subscriptions.add(new SubscriptionJsonWithEvents(bundle.getBundleId(), subscriptionTimeline,
                                                                  subscriptionsAuditLogs.get(subscriptionTimeline.getId()), subscriptionEventsAuditLogs));
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final BundleJsonWithSubscriptions that = (BundleJsonWithSubscriptions) o;

        if (subscriptions != null ? !subscriptions.equals(that.subscriptions) : that.subscriptions != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (subscriptions != null ? subscriptions.hashCode() : 0);
        return result;
    }
}
