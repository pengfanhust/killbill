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

package com.ning.billing.analytics.dao;

import java.util.List;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;

import com.ning.billing.analytics.model.BusinessInvoicePayment;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.InternalTenantContextBinder;

@ExternalizedSqlViaStringTemplate3()
@RegisterMapper(BusinessInvoicePaymentMapper.class)
public interface BusinessInvoicePaymentSqlDao extends Transactional<BusinessInvoicePaymentSqlDao>, Transmogrifier {

    @SqlQuery
    BusinessInvoicePayment getInvoicePayment(@Bind("payment_id") final String paymentId,
                                             @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    List<BusinessInvoicePayment> getInvoicePaymentsForAccountByKey(@Bind("account_key") final String accountKey,
                                                                   @InternalTenantContextBinder final InternalTenantContext context);

    @SqlUpdate
    int createInvoicePayment(@BusinessInvoicePaymentBinder final BusinessInvoicePayment payment,
                             @InternalTenantContextBinder final InternalCallContext context);

    @SqlUpdate
    int deleteInvoicePayment(@Bind("payment_id") final String paymentId,
                             @InternalTenantContextBinder final InternalCallContext context);

    @SqlUpdate
    void test(@InternalTenantContextBinder final InternalTenantContext context);
}
