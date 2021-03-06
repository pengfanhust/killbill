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

package com.ning.billing.account.glue;

import org.mockito.Mockito;

import com.ning.billing.account.dao.AccountDao;
import com.ning.billing.account.dao.AccountEmailDao;
import com.ning.billing.account.dao.MockAccountDao;
import com.ning.billing.mock.glue.MockClockModule;
import com.ning.billing.util.glue.CallContextModule;

public class AccountModuleWithMocks extends DefaultAccountModule {
    @Override
    protected void installAccountDao() {
        bind(MockAccountDao.class).asEagerSingleton();
        final AccountEmailDao accountEmailDao = Mockito.mock(AccountEmailDao.class);
        bind(AccountEmailDao.class).toInstance(accountEmailDao);
        bind(AccountDao.class).to(MockAccountDao.class);
    }

    @Override
    protected void configure() {
        super.configure();
        install(new MockClockModule());
        install(new CallContextModule());
    }
}
