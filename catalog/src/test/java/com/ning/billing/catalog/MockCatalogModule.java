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

package com.ning.billing.catalog;

import org.mockito.Mockito;

import com.google.inject.AbstractModule;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogService;

public class MockCatalogModule extends AbstractModule {
    @Override
    protected void configure() {
        final Catalog catalog = Mockito.mock(Catalog.class);

        final CatalogService catalogService = Mockito.mock(CatalogService.class);
        Mockito.when(catalogService.getCurrentCatalog()).thenReturn(new MockCatalog());
        Mockito.when(catalogService.getFullCatalog()).thenReturn(catalog);
        bind(CatalogService.class).toInstance(catalogService);
    }
}
