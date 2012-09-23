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
package com.ning.billing.overdue.notification;

import static org.testng.Assert.assertEquals;

import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.junction.api.Blockable;
import com.ning.billing.ovedue.notification.OverdueCheckNotificationKey;
import com.ning.billing.util.jackson.ObjectMapper;

public class TestOverdueNotificationKeyJson {


    private final ObjectMapper mapper = new ObjectMapper();

    @Test(groups = "fast")
    public void testOverdueNotificationKeyJson() throws Exception {

        final UUID uuid = UUID.randomUUID();
        final Blockable.Type type  = Blockable.Type.SUBSCRIPTION;

        final OverdueCheckNotificationKey e = new OverdueCheckNotificationKey(uuid, type);

        final String json = mapper.writeValueAsString(e);

        final Class<?> claz = Class.forName(OverdueCheckNotificationKey.class.getName());
        final Object obj = mapper.readValue(json, claz);
        Assert.assertTrue(obj.equals(e));
    }

    @Test(groups = "fast")
    public void testOverdueNotificationKeyJsonWithNoKey() throws Exception {

        final String uuidString = "bab0fca4-c628-4997-8980-14d6c3a98c48";
        final String json = "{\"uuidKey\":\"" + uuidString +  "\"}";

        final Class<?> claz = Class.forName(OverdueCheckNotificationKey.class.getName());
        final OverdueCheckNotificationKey obj = (OverdueCheckNotificationKey) mapper.readValue(json, claz);
        assertEquals(obj.getUuidKey().toString(), uuidString);
        assertEquals(obj.getType(), Blockable.Type.SUBSCRIPTION_BUNDLE);
    }
}
