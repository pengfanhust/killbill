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

package com.ning.billing.usage.dao;

import org.joda.time.DateTime;

import com.ning.billing.util.callcontext.InternalCallContext;

/**
 * Dao to record already rolled-up usage data (rolled-up by the user).
 * For raw tracking of the data, @see TimelineEventHandler.
 */
public interface RolledUpUsageDao {

    public void record(final String sourceName, final String eventType, final String metricName, final DateTime startDate,
                       final DateTime endDate, final long value, final InternalCallContext context);
}
