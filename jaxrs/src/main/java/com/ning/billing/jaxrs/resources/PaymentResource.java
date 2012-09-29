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

package com.ning.billing.jaxrs.resources;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.servlet.ServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.jaxrs.json.ChargebackJson;
import com.ning.billing.jaxrs.json.CustomFieldJson;
import com.ning.billing.jaxrs.json.InvoiceItemJsonSimple;
import com.ning.billing.jaxrs.json.PaymentJsonSimple;
import com.ning.billing.jaxrs.json.PaymentJsonWithBundleKeys;
import com.ning.billing.jaxrs.json.RefundJson;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.Refund;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.dao.ObjectType;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.inject.Inject;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(JaxrsResource.PAYMENTS_PATH)
public class PaymentResource extends JaxRsResourceBase {

    private static final String ID_PARAM_NAME = "paymentId";
    private static final String CUSTOM_FIELD_URI = JaxrsResource.CUSTOM_FIELDS + "/{" + ID_PARAM_NAME + ":" + UUID_PATTERN + "}";
    private static final String TAG_URI = JaxrsResource.TAGS + "/{" + ID_PARAM_NAME + ":" + UUID_PATTERN + "}";

    private final PaymentApi paymentApi;
    private final InvoicePaymentApi invoicePaymentApi;
    private final AccountUserApi accountApi;

    @Inject
    public PaymentResource(final AccountUserApi accountApi,
                           final PaymentApi paymentApi,
                           final InvoicePaymentApi invoicePaymentApi,
                           final JaxrsUriBuilder uriBuilder,
                           final TagUserApi tagUserApi,
                           final CustomFieldUserApi customFieldUserApi,
                           final AuditUserApi auditUserApi,
                           final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, context);
        this.paymentApi = paymentApi;
        this.invoicePaymentApi = invoicePaymentApi;
        this.accountApi = accountApi;
    }

    @GET
    @Path("/{paymentId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response getPayment(@PathParam(ID_PARAM_NAME) final String paymentIdString,
                               @QueryParam(QUERY_PAYMENT_WITH_REFUNDS_AND_CHARGEBACKS) @DefaultValue("false") final Boolean withRefundsAndChargebacks,
                               @javax.ws.rs.core.Context final ServletRequest request) throws PaymentApiException {
        final TenantContext tenantContext = context.createContext(request);

        final UUID paymentId = UUID.fromString(paymentIdString);
        final Payment payment = paymentApi.getPayment(paymentId, tenantContext);

        final PaymentJsonSimple paymentJsonSimple;
        if (withRefundsAndChargebacks) {
            final List<RefundJson> refunds = new ArrayList<RefundJson>();
            for (final Refund refund : paymentApi.getPaymentRefunds(paymentId, tenantContext)) {
                refunds.add(new RefundJson(refund));
            }

            final List<ChargebackJson> chargebacks = new ArrayList<ChargebackJson>();
            for (final InvoicePayment chargeback : invoicePaymentApi.getChargebacksByPaymentId(paymentId, tenantContext)) {
                chargebacks.add(new ChargebackJson(chargeback));
            }

            final int nbOfPaymentAttempts = payment.getAttempts().size();
            final String status = payment.getPaymentStatus().toString();
            paymentJsonSimple = new PaymentJsonWithBundleKeys(payment,
                                                              status,
                                                              nbOfPaymentAttempts,
                                                              null, // TODO - the keys are really only used for the timeline
                                                              payment.getAccountId(),
                                                              refunds,
                                                              chargebacks);
        } else {
            paymentJsonSimple = new PaymentJsonSimple(payment);
        }

        return Response.status(Status.OK).entity(paymentJsonSimple).build();
    }

    @GET
    @Path("/{paymentId:" + UUID_PATTERN + "}/" + REFUNDS)
    @Produces(APPLICATION_JSON)
    public Response getRefunds(@PathParam("paymentId") final String paymentId,
                               @javax.ws.rs.core.Context final ServletRequest request) throws PaymentApiException {
        final List<Refund> refunds = paymentApi.getPaymentRefunds(UUID.fromString(paymentId), context.createContext(request));
        final List<RefundJson> result = new ArrayList<RefundJson>(Collections2.transform(refunds, new Function<Refund, RefundJson>() {
            @Override
            public RefundJson apply(final Refund input) {
                // TODO Return adjusted items and audits
                return new RefundJson(input, null, null);
            }
        }));

        return Response.status(Status.OK).entity(result).build();
    }

    @POST
    @Path("/{paymentId:" + UUID_PATTERN + "}/" + REFUNDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createRefund(final RefundJson json,
                                 @PathParam("paymentId") final String paymentId,
                                 @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                 @HeaderParam(HDR_REASON) final String reason,
                                 @HeaderParam(HDR_COMMENT) final String comment,
                                 @javax.ws.rs.core.Context final UriInfo uriInfo,
                                 @javax.ws.rs.core.Context final ServletRequest request) throws PaymentApiException, AccountApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final UUID paymentUuid = UUID.fromString(paymentId);
        final Payment payment = paymentApi.getPayment(paymentUuid, callContext);
        final Account account = accountApi.getAccountById(payment.getAccountId(), callContext);

        final Refund result;
        if (json.isAdjusted()) {
            if (json.getAdjustments() != null && json.getAdjustments().size() > 0) {
                final Map<UUID, BigDecimal> adjustments = new HashMap<UUID, BigDecimal>();
                for (final InvoiceItemJsonSimple item : json.getAdjustments()) {
                    adjustments.put(UUID.fromString(item.getInvoiceItemId()), item.getAmount());
                }
                result = paymentApi.createRefundWithItemsAdjustments(account, paymentUuid, adjustments, callContext);
            } else {
                // Invoice adjustment
                result = paymentApi.createRefundWithAdjustment(account, paymentUuid, json.getAmount(), callContext);
            }
        } else {
            // Refund without adjustment
            result = paymentApi.createRefund(account, paymentUuid, json.getAmount(), callContext);
        }

        return uriBuilder.buildResponse(RefundResource.class, "getRefund", result.getId(), uriInfo.getBaseUri().toString());
    }

    @GET
    @Path(CUSTOM_FIELD_URI)
    @Produces(APPLICATION_JSON)
    public Response getCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                    @javax.ws.rs.core.Context final ServletRequest request) {
        return super.getCustomFields(UUID.fromString(id), context.createContext(request));
    }

    @POST
    @Path(CUSTOM_FIELD_URI)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                       final List<CustomFieldJson> customFields,
                                       @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                       @HeaderParam(HDR_REASON) final String reason,
                                       @HeaderParam(HDR_COMMENT) final String comment,
                                       @javax.ws.rs.core.Context final ServletRequest request) {
        return super.createCustomFields(UUID.fromString(id), customFields,
                                        context.createContext(createdBy, reason, comment, request));
    }

    @DELETE
    @Path(CUSTOM_FIELD_URI)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response deleteCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                       @QueryParam(QUERY_CUSTOM_FIELDS) final String customFieldList,
                                       @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                       @HeaderParam(HDR_REASON) final String reason,
                                       @HeaderParam(HDR_COMMENT) final String comment,
                                       @javax.ws.rs.core.Context final ServletRequest request) {
        return super.deleteCustomFields(UUID.fromString(id), customFieldList,
                                        context.createContext(createdBy, reason, comment, request));
    }

    @GET
    @Path(TAG_URI)
    @Produces(APPLICATION_JSON)
    public Response getTags(@PathParam(ID_PARAM_NAME) final String id,
                            @QueryParam(QUERY_AUDIT) @DefaultValue("false") final Boolean withAudit,
                            @javax.ws.rs.core.Context final ServletRequest request) throws TagDefinitionApiException {
        return super.getTags(UUID.fromString(id), withAudit, context.createContext(request));
    }

    @POST
    @Path(TAG_URI)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createTags(@PathParam(ID_PARAM_NAME) final String id,
                               @QueryParam(QUERY_TAGS) final String tagList,
                               @HeaderParam(HDR_CREATED_BY) final String createdBy,
                               @HeaderParam(HDR_REASON) final String reason,
                               @HeaderParam(HDR_COMMENT) final String comment,
                               @javax.ws.rs.core.Context final UriInfo uriInfo,
                               @javax.ws.rs.core.Context final ServletRequest request) throws TagApiException {
        return super.createTags(UUID.fromString(id), tagList, uriInfo,
                                context.createContext(createdBy, reason, comment, request));
    }

    @DELETE
    @Path(TAG_URI)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response deleteTags(@PathParam(ID_PARAM_NAME) final String id,
                               @QueryParam(QUERY_TAGS) final String tagList,
                               @HeaderParam(HDR_CREATED_BY) final String createdBy,
                               @HeaderParam(HDR_REASON) final String reason,
                               @HeaderParam(HDR_COMMENT) final String comment,
                               @javax.ws.rs.core.Context final ServletRequest request) throws TagApiException {
        return super.deleteTags(UUID.fromString(id), tagList,
                                context.createContext(createdBy, reason, comment, request));
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.PAYMENT;
    }
}
