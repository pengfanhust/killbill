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

package com.ning.billing.account.api.user;

import java.util.UUID;

import org.joda.time.DateTimeZone;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountCreationEvent;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.BillCycleDay;
import com.ning.billing.account.api.DefaultBillCycleDay;
import com.ning.billing.catalog.api.Currency;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DefaultAccountCreationEvent implements AccountCreationEvent {

    private final UUID userToken;
    private final UUID id;
    private final AccountData data;

    @JsonCreator
    public DefaultAccountCreationEvent(@JsonProperty("data") final DefaultAccountData data,
                                       @JsonProperty("userToken") final UUID userToken,
                                       @JsonProperty("id") final UUID id) {
        this.id = id;
        this.userToken = userToken;
        this.data = data;
    }

    public DefaultAccountCreationEvent(final Account data, final UUID userToken) {
        this.id = data.getId();
        this.data = new DefaultAccountData(data);
        this.userToken = userToken;
    }

    @JsonIgnore
    @Override
    public BusEventType getBusEventType() {
        return BusEventType.ACCOUNT_CREATE;
    }

    @Override
    public UUID getUserToken() {
        return userToken;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public AccountData getData() {
        return data;
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((data == null) ? 0 : data.hashCode());
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result
                + ((userToken == null) ? 0 : userToken.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DefaultAccountCreationEvent other = (DefaultAccountCreationEvent) obj;
        if (data == null) {
            if (other.data != null) {
                return false;
            }
        } else if (!data.equals(other.data)) {
            return false;
        }
        if (id == null) {
            if (other.id != null) {
                return false;
            }
        } else if (!id.equals(other.id)) {
            return false;
        }
        if (userToken == null) {
            if (other.userToken != null) {
                return false;
            }
        } else if (!userToken.equals(other.userToken)) {
            return false;
        }
        return true;
    }


    public static class DefaultAccountData implements AccountData {
        private final String externalKey;
        private final String name;
        private final Integer firstNameLength;
        private final String email;
        private final DefaultBillCycleDay billCycleDay;
        private final String currency;
        private final UUID paymentMethodId;
        private final String timeZone;
        private final String locale;
        private final String address1;
        private final String address2;
        private final String companyName;
        private final String city;
        private final String stateOrProvince;
        private final String postalCode;
        private final String country;
        private final String phone;
        private final boolean isMigrated;
        private final boolean isNotifiedForInvoices;

        public DefaultAccountData(final Account d) {
            this(d.getExternalKey() != null ? d.getExternalKey() : null,
                 d.getName(),
                 d.getFirstNameLength(),
                 d.getEmail(),
                 new DefaultBillCycleDay(d.getBillCycleDay()),
                 d.getCurrency() != null ? d.getCurrency().name() : null,
                 d.getPaymentMethodId(),
                 d.getTimeZone() != null ? d.getTimeZone().getID() : null,
                 d.getLocale(),
                 d.getAddress1(),
                 d.getAddress2(),
                 d.getCompanyName(),
                 d.getCity(),
                 d.getStateOrProvince(),
                 d.getPostalCode(),
                 d.getCountry(),
                 d.getPhone(),
                 d.isMigrated(),
                 d.isNotifiedForInvoices());
        }

        @JsonCreator
        public DefaultAccountData(@JsonProperty("externalKey") final String externalKey,
                                  @JsonProperty("name") final String name,
                                  @JsonProperty("firstNameLength") final Integer firstNameLength,
                                  @JsonProperty("email") final String email,
                                  @JsonProperty("billCycleDay") final DefaultBillCycleDay billCycleDay,
                                  @JsonProperty("currency") final String currency,
                                  @JsonProperty("paymentMethodId") final UUID paymentMethodId,
                                  @JsonProperty("timeZone") final String timeZone,
                                  @JsonProperty("locale") final String locale,
                                  @JsonProperty("address1") final String address1,
                                  @JsonProperty("address2") final String address2,
                                  @JsonProperty("companyName") final String companyName,
                                  @JsonProperty("city") final String city,
                                  @JsonProperty("stateOrProvince") final String stateOrProvince,
                                  @JsonProperty("postalCode") final String postalCode,
                                  @JsonProperty("country") final String country,
                                  @JsonProperty("phone") final String phone,
                                  @JsonProperty("isMigrated") final boolean isMigrated,
                                  @JsonProperty("isNotifiedForInvoices") final boolean isNotifiedForInvoices) {
            super();
            this.externalKey = externalKey;
            this.name = name;
            this.firstNameLength = firstNameLength;
            this.email = email;
            this.billCycleDay = billCycleDay;
            this.currency = currency;
            this.paymentMethodId = paymentMethodId;
            this.timeZone = timeZone;
            this.locale = locale;
            this.address1 = address1;
            this.address2 = address2;
            this.companyName = companyName;
            this.city = city;
            this.stateOrProvince = stateOrProvince;
            this.postalCode = postalCode;
            this.country = country;
            this.phone = phone;
            this.isMigrated = isMigrated;
            this.isNotifiedForInvoices = isNotifiedForInvoices;
        }

        @Override
        public String getExternalKey() {
            return externalKey;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Integer getFirstNameLength() {
            return firstNameLength;
        }

        @Override
        public String getEmail() {
            return email;
        }

        @Override
        public BillCycleDay getBillCycleDay() {
            return billCycleDay;
        }

        @Override
        public Currency getCurrency() {
            return Currency.valueOf(currency);
        }

        @JsonIgnore
        @Override
        public DateTimeZone getTimeZone() {
            return DateTimeZone.forID(timeZone);
        }

        @JsonProperty("timeZone")
        public String getTimeZoneString() {
            return timeZone;
        }

        @Override
        public String getLocale() {
            return locale;
        }

        @Override
        public String getAddress1() {
            return address1;
        }

        @Override
        public String getAddress2() {
            return address2;
        }

        @Override
        public String getCompanyName() {
            return companyName;
        }

        @Override
        public String getCity() {
            return city;
        }

        @Override
        public String getStateOrProvince() {
            return stateOrProvince;
        }

        @Override
        public String getPostalCode() {
            return postalCode;
        }

        @Override
        public String getCountry() {
            return country;
        }

        @Override
        public String getPhone() {
            return phone;
        }

        @Override
        public UUID getPaymentMethodId() {
            return paymentMethodId;
        }

        @Override
        @JsonIgnore
        public Boolean isMigrated() {
            return isMigrated;
        }

        @Override
        @JsonIgnore
        public Boolean isNotifiedForInvoices() {
            return isNotifiedForInvoices;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result
                    + ((address1 == null) ? 0 : address1.hashCode());
            result = prime * result
                    + ((address2 == null) ? 0 : address2.hashCode());
            result = prime * result
                    + ((billCycleDay == null) ? 0 : billCycleDay.hashCode());
            result = prime * result + ((city == null) ? 0 : city.hashCode());
            result = prime * result
                    + ((companyName == null) ? 0 : companyName.hashCode());
            result = prime * result
                    + ((country == null) ? 0 : country.hashCode());
            result = prime * result
                    + ((currency == null) ? 0 : currency.hashCode());
            result = prime * result + ((email == null) ? 0 : email.hashCode());
            result = prime * result
                    + ((externalKey == null) ? 0 : externalKey.hashCode());
            result = prime
                    * result
                    + ((firstNameLength == null) ? 0 : firstNameLength
                    .hashCode());
            result = prime * result
                    + ((locale == null) ? 0 : locale.hashCode());
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            result = prime
                    * result
                    + ((paymentMethodId == null) ? 0 : paymentMethodId
                    .hashCode());
            result = prime * result + ((phone == null) ? 0 : phone.hashCode());
            result = prime * result
                    + ((postalCode == null) ? 0 : postalCode.hashCode());
            result = prime
                    * result
                    + ((stateOrProvince == null) ? 0 : stateOrProvince
                    .hashCode());
            result = prime * result
                    + ((timeZone == null) ? 0 : timeZone.hashCode());
            return result;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final DefaultAccountData other = (DefaultAccountData) obj;
            if (address1 == null) {
                if (other.address1 != null) {
                    return false;
                }
            } else if (!address1.equals(other.address1)) {
                return false;
            }
            if (address2 == null) {
                if (other.address2 != null) {
                    return false;
                }
            } else if (!address2.equals(other.address2)) {
                return false;
            }
            if (billCycleDay == null) {
                if (other.billCycleDay != null) {
                    return false;
                }
            } else if (!billCycleDay.equals(other.billCycleDay)) {
                return false;
            }
            if (city == null) {
                if (other.city != null) {
                    return false;
                }
            } else if (!city.equals(other.city)) {
                return false;
            }
            if (companyName == null) {
                if (other.companyName != null) {
                    return false;
                }
            } else if (!companyName.equals(other.companyName)) {
                return false;
            }
            if (country == null) {
                if (other.country != null) {
                    return false;
                }
            } else if (!country.equals(other.country)) {
                return false;
            }
            if (currency == null) {
                if (other.currency != null) {
                    return false;
                }
            } else if (!currency.equals(other.currency)) {
                return false;
            }
            if (email == null) {
                if (other.email != null) {
                    return false;
                }
            } else if (!email.equals(other.email)) {
                return false;
            }
            if (externalKey == null) {
                if (other.externalKey != null) {
                    return false;
                }
            } else if (!externalKey.equals(other.externalKey)) {
                return false;
            }
            if (firstNameLength == null) {
                if (other.firstNameLength != null) {
                    return false;
                }
            } else if (!firstNameLength.equals(other.firstNameLength)) {
                return false;
            }
            if (locale == null) {
                if (other.locale != null) {
                    return false;
                }
            } else if (!locale.equals(other.locale)) {
                return false;
            }
            if (name == null) {
                if (other.name != null) {
                    return false;
                }
            } else if (!name.equals(other.name)) {
                return false;
            }
            if (paymentMethodId == null) {
                if (other.paymentMethodId != null) {
                    return false;
                }
            } else if (!paymentMethodId.equals(other.paymentMethodId)) {
                return false;
            }
            if (phone == null) {
                if (other.phone != null) {
                    return false;
                }
            } else if (!phone.equals(other.phone)) {
                return false;
            }
            if (postalCode == null) {
                if (other.postalCode != null) {
                    return false;
                }
            } else if (!postalCode.equals(other.postalCode)) {
                return false;
            }
            if (stateOrProvince == null) {
                if (other.stateOrProvince != null) {
                    return false;
                }
            } else if (!stateOrProvince.equals(other.stateOrProvince)) {
                return false;
            }
            if (timeZone == null) {
                if (other.timeZone != null) {
                    return false;
                }
            } else if (!timeZone.equals(other.timeZone)) {
                return false;
            }
            return true;
        }
    }
}
