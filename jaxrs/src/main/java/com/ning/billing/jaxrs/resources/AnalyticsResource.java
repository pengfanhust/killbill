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

package com.ning.billing.jaxrs.resources;

import javax.inject.Inject;
import javax.servlet.ServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.ning.billing.analytics.api.TimeSeriesData;
import com.ning.billing.analytics.api.user.AnalyticsUserApi;
import com.ning.billing.jaxrs.json.TimeSeriesDataJson;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.TagUserApi;

import com.google.inject.Singleton;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JaxrsResource.ANALYTICS_PATH)
public class AnalyticsResource extends JaxRsResourceBase {

    private final AnalyticsUserApi analyticsUserApi;

    @Inject
    public AnalyticsResource(final AnalyticsUserApi analyticsUserApi,
                             final JaxrsUriBuilder uriBuilder,
                             final TagUserApi tagUserApi,
                             final CustomFieldUserApi customFieldUserApi,
                             final AuditUserApi auditUserApi,
                             final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, context);
        this.analyticsUserApi = analyticsUserApi;
    }

    @GET
    @Path("/accountsCreatedOverTime")
    @Produces(APPLICATION_JSON)
    public Response getAccountsCreatedOverTime(@javax.ws.rs.core.Context final ServletRequest request) {
        final TimeSeriesData data = analyticsUserApi.getAccountsCreatedOverTime(context.createContext(request));
        final TimeSeriesDataJson json = new TimeSeriesDataJson(data);
        return Response.status(Status.OK).entity(json).build();
    }

    @GET
    @Path("/subscriptionsCreatedOverTime")
    @Produces(APPLICATION_JSON)
    public Response getSubscriptionsCreatedOverTime(@QueryParam("productType") final String productType,
                                                    @QueryParam("slug") final String slug,
                                                    @javax.ws.rs.core.Context final ServletRequest request) {
        final TimeSeriesData data = analyticsUserApi.getSubscriptionsCreatedOverTime(productType, slug, context.createContext(request));
        final TimeSeriesDataJson json = new TimeSeriesDataJson(data);
        return Response.status(Status.OK).entity(json).build();
    }
}
