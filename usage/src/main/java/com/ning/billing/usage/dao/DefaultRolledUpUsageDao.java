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

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;

import com.ning.billing.usage.timeline.persistent.TimelineSqlDao;
import com.ning.billing.util.callcontext.InternalCallContext;

public class DefaultRolledUpUsageDao implements RolledUpUsageDao {

    private final RolledUpUsageSqlDao rolledUpUsageSqlDao;

    @Inject
    public DefaultRolledUpUsageDao(final RolledUpUsageSqlDao rolledUpUsageSqlDao) {
        this.rolledUpUsageSqlDao = rolledUpUsageSqlDao;
    }

    @Override
    public void record(final String source, final String eventType, final String metricName, final DateTime startDate,
                       final DateTime endDate, final long value, final InternalCallContext context) {
        rolledUpUsageSqlDao.inTransaction(new Transaction<Void, RolledUpUsageSqlDao>() {
            @Override
            public Void inTransaction(final RolledUpUsageSqlDao transactional, final TransactionStatus status) throws Exception {
                final TimelineSqlDao timelineSqlDao = transactional.become(TimelineSqlDao.class);

                // Create the source if it doesn't exist
                Integer sourceId = timelineSqlDao.getSourceId(source, context);
                if (sourceId == null) {
                    timelineSqlDao.addSource(source, context);
                    sourceId = timelineSqlDao.getSourceId(source, context);
                }

                // Create the category if it doesn't exist
                Integer categoryId = timelineSqlDao.getEventCategoryId(eventType, context);
                if (categoryId == null) {
                    timelineSqlDao.addEventCategory(eventType, context);
                    categoryId = timelineSqlDao.getEventCategoryId(eventType, context);
                }

                // Create the metric if it doesn't exist
                Integer metricId = timelineSqlDao.getMetricId(categoryId, metricName, context);
                if (metricId == null) {
                    timelineSqlDao.addMetric(categoryId, metricName, context);
                    metricId = timelineSqlDao.getMetricId(categoryId, metricName, context);
                }

                transactional.record(sourceId, metricId, startDate.toDate(), endDate.toDate(), value, context);

                return null;
            }
        });
    }
}
