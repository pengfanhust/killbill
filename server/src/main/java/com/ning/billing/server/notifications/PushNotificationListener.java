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
package com.ning.billing.server.notifications;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.beatrix.bus.api.ExtBusEvent;
import com.ning.billing.jaxrs.json.NotificationJson;
import com.ning.billing.tenant.api.TenantApiException;
import com.ning.billing.tenant.api.TenantKV.TenantKey;
import com.ning.billing.tenant.api.TenantUserApi;
import com.ning.billing.util.callcontext.CallContextFactory;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClient.BoundRequestBuilder;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.Response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.eventbus.Subscribe;

public class PushNotificationListener {

    private final static Logger log = LoggerFactory.getLogger(PushNotificationListener.class);

    private final static int TIMEOUT_NOTIFCATION = 15; // 15 seconds

    private final TenantUserApi tenantApi;
    private final CallContextFactory contextFactory;
    private final AsyncHttpClient httpClient;
    private final ObjectMapper mapper;


    @Inject
    public PushNotificationListener(final ObjectMapper mapper, final TenantUserApi tenantApi, final CallContextFactory contextFactory) {
        this.httpClient = new AsyncHttpClient(new AsyncHttpClientConfig.Builder().setRequestTimeoutInMs(TIMEOUT_NOTIFCATION * 1000).build());
        this.tenantApi = tenantApi;
        this.contextFactory = contextFactory;
        this.mapper = mapper;
    }

    @Subscribe
    public void triggerPushNotifications(final ExtBusEvent event) {
        final TenantContext context = contextFactory.createTenantContext(event.getTenantId());
        try {
            final List<String> callbacks = getCallbacksForTenant(context);
            dispatchCallback(event.getTenantId(), event, callbacks);
        } catch (final TenantApiException e) {
            log.warn("Failed to retrieve push notification callback for tenant {}", event.getTenantId());
        } catch (final IOException e) {
            log.warn("Failed to retrieve push notification callback for tenant {}", event.getTenantId());
        }
    }

    private void dispatchCallback(final UUID tenantId, final ExtBusEvent event, final List<String> callbacks) throws IOException {
        final NotificationJson notification = new NotificationJson(event);
        final String body = mapper.writeValueAsString(notification);
        for (final String cur : callbacks) {
            doPost(tenantId, cur, body, TIMEOUT_NOTIFCATION);
        }
    }


    private boolean doPost(final UUID tenantId, final String url, final String body, final int timeoutSec) {

        final BoundRequestBuilder builder = httpClient.preparePost(url);
        builder.setBody(body == null ? "{}" : body);

        Response response = null;
        try {
            final ListenableFuture<Response> futureStatus =
                    builder.execute(new AsyncCompletionHandler<Response>() {
                        @Override
                        public Response onCompleted(final Response response) throws Exception {
                            return response;
                        }
                    });
            response = futureStatus.get(timeoutSec, TimeUnit.SECONDS);
        } catch (final Exception e) {
            log.warn(String.format("Fail to psh notification {} for the tenant {} ", url, tenantId), e);
            return false;
        }
        return response.getStatusCode() >= 200 && response.getStatusCode() < 300;
    }

    private List<String> getCallbacksForTenant(final TenantContext context) throws TenantApiException {
        return tenantApi.getTenantValueForKey(TenantKey.PUSH_NOTIFICATION_CB.toString(), context);
    }
}
