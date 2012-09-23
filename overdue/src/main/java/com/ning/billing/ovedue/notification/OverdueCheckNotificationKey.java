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

package com.ning.billing.ovedue.notification;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.ning.billing.junction.api.Blockable;
import com.ning.billing.util.notificationq.DefaultUUIDNotificationKey;

public class OverdueCheckNotificationKey extends DefaultUUIDNotificationKey {

    private final Blockable.Type type;

    @JsonCreator
    public OverdueCheckNotificationKey(@JsonProperty("uuidKey") UUID uuidKey,
            @JsonProperty("type") Blockable.Type type) {
        super(uuidKey);
        this.type = type;
    }

    // Hack : We default to SubscriptionBundle which is the only one supported at the time
    public Blockable.Type getType() {
        return type == null ? Blockable.Type.SUBSCRIPTION_BUNDLE : type;
    }
}
