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

package com.ning.billing.util.notificationq;

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.util.entity.Entity;
import com.ning.billing.util.queue.PersistentQueueEntryLifecycle;

public interface Notification extends PersistentQueueEntryLifecycle, Entity {

    public Long getOrdering();

    public String getNotificationKeyClass();

    public String getNotificationKey();

    public DateTime getEffectiveDate();

    public String getQueueName();

    // TODO - do we still need it now we have account_record_id?
    public UUID getAccountId();
}
