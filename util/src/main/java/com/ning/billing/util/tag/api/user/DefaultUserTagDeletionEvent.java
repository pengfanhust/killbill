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

package com.ning.billing.util.tag.api.user;

import java.util.UUID;

import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.tag.TagDefinition;
import com.ning.billing.util.tag.api.UserTagDeletionEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DefaultUserTagDeletionEvent implements UserTagDeletionEvent {
    private final UUID tagId;
    private final UUID objectId;
    private final ObjectType objectType;
    private final TagDefinition tagDefinition;
    private final UUID userToken;

    @JsonCreator
    public DefaultUserTagDeletionEvent(@JsonProperty("tagId") final UUID tagId,
                                       @JsonProperty("objectId") final UUID objectId,
                                       @JsonProperty("objectType") final ObjectType objectType,
                                       @JsonProperty("tagDefinition") final TagDefinition tagDefinition,
                                       @JsonProperty("userToken") final UUID userToken) {
        this.tagId = tagId;
        this.objectId = objectId;
        this.objectType = objectType;
        this.tagDefinition = tagDefinition;
        this.userToken = userToken;
    }

    @Override
    public UUID getTagId() {
        return tagId;
    }

    @Override
    public UUID getObjectId() {
        return objectId;
    }

    @Override
    public ObjectType getObjectType() {
        return objectType;
    }

    @Override
    public TagDefinition getTagDefinition() {
        return tagDefinition;
    }

    @JsonIgnore
    @Override
    public BusEventType getBusEventType() {
        return BusEventType.USER_TAG_DELETION;
    }

    @Override
    public UUID getUserToken() {
        return userToken;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DefaultUserTagDeletionEvent");
        sb.append("{objectId=").append(objectId);
        sb.append(", tagId=").append(tagId);
        sb.append(", objectType=").append(objectType);
        sb.append(", tagDefinition=").append(tagDefinition);
        sb.append(", userToken=").append(userToken);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DefaultUserTagDeletionEvent that = (DefaultUserTagDeletionEvent) o;

        if (objectId != null ? !objectId.equals(that.objectId) : that.objectId != null) {
            return false;
        }
        if (objectType != that.objectType) {
            return false;
        }
        if (tagDefinition != null ? !tagDefinition.equals(that.tagDefinition) : that.tagDefinition != null) {
            return false;
        }
        if (tagId != null ? !tagId.equals(that.tagId) : that.tagId != null) {
            return false;
        }
        if (userToken != null ? !userToken.equals(that.userToken) : that.userToken != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = tagId != null ? tagId.hashCode() : 0;
        result = 31 * result + (objectId != null ? objectId.hashCode() : 0);
        result = 31 * result + (objectType != null ? objectType.hashCode() : 0);
        result = 31 * result + (tagDefinition != null ? tagDefinition.hashCode() : 0);
        result = 31 * result + (userToken != null ? userToken.hashCode() : 0);
        return result;
    }
}
