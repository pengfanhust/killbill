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

package com.ning.billing.usage.timeline.persistent;

import java.util.List;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.exceptions.CallbackFailedException;
import org.skife.jdbi.v2.exceptions.UnableToObtainConnectionException;

import com.ning.billing.usage.timeline.categories.CategoryIdAndMetric;
import com.ning.billing.usage.timeline.chunks.TimelineChunk;
import com.ning.billing.usage.timeline.consumer.TimelineChunkConsumer;
import com.ning.billing.usage.timeline.shutdown.StartTimes;
import com.ning.billing.usage.timeline.sources.SourceIdAndMetricId;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;

import com.google.common.collect.BiMap;

public interface TimelineDao {

    // Sources table

    Integer getSourceId(String source, InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException;

    String getSource(Integer sourceId, InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException;

    BiMap<Integer, String> getSources(InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException;

    int getOrAddSource(String source, InternalCallContext context) throws UnableToObtainConnectionException, CallbackFailedException;

    // Event categories table

    Integer getEventCategoryId(String eventCategory, InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException;

    String getEventCategory(Integer eventCategoryId, InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException;

    BiMap<Integer, String> getEventCategories(InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException;

    int getOrAddEventCategory(String eventCategory, InternalCallContext context) throws UnableToObtainConnectionException, CallbackFailedException;

    // Sample kinds table

    Integer getMetricId(int eventCategory, String metric, InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException;

    CategoryIdAndMetric getCategoryIdAndMetric(Integer metricId, InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException;

    BiMap<Integer, CategoryIdAndMetric> getMetrics(InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException;

    int getOrAddMetric(Integer sourceId, Integer eventCategoryId, String metric, InternalCallContext context) throws UnableToObtainConnectionException, CallbackFailedException;

    Iterable<Integer> getMetricIdsBySourceId(Integer sourceId, InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException;

    Iterable<SourceIdAndMetricId> getMetricIdsForAllSources(InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException;

    // Timelines tables

    Long insertTimelineChunk(TimelineChunk timelineChunk, InternalCallContext context) throws UnableToObtainConnectionException, CallbackFailedException;

    void getSamplesBySourceIdsAndMetricIds(List<Integer> sourceIds,
                                           @Nullable List<Integer> metricIds,
                                           DateTime startTime,
                                           DateTime endTime,
                                           TimelineChunkConsumer chunkConsumer,
                                           InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException;

    Integer insertLastStartTimes(StartTimes startTimes, InternalCallContext context);

    StartTimes getLastStartTimes(InternalTenantContext context);

    void deleteLastStartTimes(InternalCallContext context);

    void bulkInsertSources(List<String> sources, InternalCallContext context) throws UnableToObtainConnectionException, CallbackFailedException;

    void bulkInsertEventCategories(List<String> categoryNames, InternalCallContext context) throws UnableToObtainConnectionException, CallbackFailedException;

    void bulkInsertMetrics(List<CategoryIdAndMetric> categoryAndKinds, InternalCallContext context);

    void bulkInsertTimelineChunks(List<TimelineChunk> timelineChunkList, InternalCallContext context);

    void test(InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException;
}
