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

package com.ning.billing.junction.plumbing.billing;

import java.math.BigDecimal;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.BillCycleDay;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.util.events.EffectiveSubscriptionInternalEvent;
import com.ning.billing.util.svcapi.junction.BillingEvent;
import com.ning.billing.util.svcapi.junction.BillingModeType;

public class DefaultBillingEvent implements BillingEvent {
    private final Account account;
    private final BillCycleDay billCycleDay;
    private final Subscription subscription;
    private final DateTime effectiveDate;
    private final PlanPhase planPhase;
    private final Plan plan;
    private final BigDecimal fixedPrice;
    private final BigDecimal recurringPrice;
    private final Currency currency;
    private final String description;
    private final BillingModeType billingModeType;
    private final BillingPeriod billingPeriod;
    private final SubscriptionTransitionType type;
    private final Long totalOrdering;
    private final DateTimeZone timeZone;

    public DefaultBillingEvent(final Account account, final EffectiveSubscriptionInternalEvent transition, final Subscription subscription, final BillCycleDay billCycleDay, final Currency currency, final Catalog catalog) throws CatalogApiException {

        this.account = account;
        this.billCycleDay = billCycleDay;
        this.subscription = subscription;
        effectiveDate = transition.getEffectiveTransitionTime();
        final String planPhaseName = (transition.getTransitionType() != SubscriptionTransitionType.CANCEL) ?
                transition.getNextPhase() : transition.getPreviousPhase();
        planPhase = (planPhaseName != null) ? catalog.findPhase(planPhaseName, transition.getEffectiveTransitionTime(), transition.getSubscriptionStartDate()) : null;

        final String planName = (transition.getTransitionType() != SubscriptionTransitionType.CANCEL) ?
                transition.getNextPlan() : transition.getPreviousPlan();
        plan = (planName != null) ? catalog.findPlan(planName, transition.getEffectiveTransitionTime(), transition.getSubscriptionStartDate()) : null;

        final String nextPhaseName = transition.getNextPhase();
        final PlanPhase nextPhase = (nextPhaseName != null) ? catalog.findPhase(nextPhaseName, transition.getEffectiveTransitionTime(), transition.getSubscriptionStartDate()) : null;

        final String prevPhaseName = transition.getPreviousPhase();
        final PlanPhase prevPhase = (prevPhaseName != null) ? catalog.findPhase(prevPhaseName, transition.getEffectiveTransitionTime(), transition.getSubscriptionStartDate()) : null;


        fixedPrice = (nextPhase != null && nextPhase.getFixedPrice() != null) ? nextPhase.getFixedPrice().getPrice(currency) : null;
        recurringPrice = (nextPhase != null && nextPhase.getRecurringPrice() != null) ? nextPhase.getRecurringPrice().getPrice(currency) : null;

        this.currency = currency;
        description = transition.getTransitionType().toString();
        billingModeType = BillingModeType.IN_ADVANCE;
        billingPeriod = (transition.getTransitionType() != SubscriptionTransitionType.CANCEL) ?
                nextPhase.getBillingPeriod() : prevPhase.getBillingPeriod();
        type = transition.getTransitionType();
        totalOrdering = transition.getTotalOrdering();
        timeZone = account.getTimeZone();
    }

    public DefaultBillingEvent(final Account account, final Subscription subscription, final DateTime effectiveDate, final Plan plan, final PlanPhase planPhase,
                               final BigDecimal fixedPrice, final BigDecimal recurringPrice, final Currency currency,
                               final BillingPeriod billingPeriod, final BillCycleDay billCycleDay, final BillingModeType billingModeType,
                               final String description, final long totalOrdering, final SubscriptionTransitionType type, final DateTimeZone timeZone) {
        this.account = account;
        this.subscription = subscription;
        this.effectiveDate = effectiveDate;
        this.plan = plan;
        this.planPhase = planPhase;
        this.fixedPrice = fixedPrice;
        this.recurringPrice = recurringPrice;
        this.currency = currency;
        this.billingPeriod = billingPeriod;
        this.billCycleDay = billCycleDay;
        this.billingModeType = billingModeType;
        this.description = description;
        this.type = type;
        this.totalOrdering = totalOrdering;
        this.timeZone = timeZone;
    }

    @Override
    public int compareTo(final BillingEvent e1) {
        if (!getSubscription().getId().equals(e1.getSubscription().getId())) { // First order by subscription
            return getSubscription().getId().compareTo(e1.getSubscription().getId());
        } else { // subscriptions are the same
            if (!getEffectiveDate().equals(e1.getEffectiveDate())) { // Secondly order by date
                return getEffectiveDate().compareTo(e1.getEffectiveDate());
            } else { // dates and subscriptions are the same
                // If an entitlement event and an overdue event happen at the exact same time,
                // we assume we want the entitlement event before the overdue event when entering
                // the overdue period, and vice-versa when exiting the overdue period
                if (SubscriptionTransitionType.START_BILLING_DISABLED.equals(getTransitionType())) {
                    if (SubscriptionTransitionType.END_BILLING_DISABLED.equals(e1.getTransitionType())) {
                        // Make sure to always have START before END
                        return -1;
                    } else {
                        return 1;
                    }
                } else if (SubscriptionTransitionType.START_BILLING_DISABLED.equals(e1.getTransitionType())) {
                    if (SubscriptionTransitionType.END_BILLING_DISABLED.equals(getTransitionType())) {
                        // Make sure to always have START before END
                        return 1;
                    } else {
                        return -1;
                    }
                } else if (SubscriptionTransitionType.END_BILLING_DISABLED.equals(getTransitionType())) {
                    if (SubscriptionTransitionType.START_BILLING_DISABLED.equals(e1.getTransitionType())) {
                        // Make sure to always have START before END
                        return 1;
                    } else {
                        return -1;
                    }
                } else if (SubscriptionTransitionType.END_BILLING_DISABLED.equals(e1.getTransitionType())) {
                    if (SubscriptionTransitionType.START_BILLING_DISABLED.equals(getTransitionType())) {
                        // Make sure to always have START before END
                        return -1;
                    } else {
                        return 1;
                    }
                } else {
                    return getTotalOrdering().compareTo(e1.getTotalOrdering());
                }
            }
        }
    }

    @Override
    public Account getAccount() {
        return account;
    }

    @Override
    public BillCycleDay getBillCycleDay() {
        return billCycleDay;
    }

    @Override
    public Subscription getSubscription() {
        return subscription;
    }

    @Override
    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    @Override
    public PlanPhase getPlanPhase() {
        return planPhase;
    }

    @Override
    public Plan getPlan() {
        return plan;
    }

    @Override
    public BillingPeriod getBillingPeriod() {
        return billingPeriod;
    }

    @Override
    public BillingModeType getBillingMode() {
        return billingModeType;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public BigDecimal getFixedPrice() {
        return fixedPrice;
    }

    @Override
    public BigDecimal getRecurringPrice() {
        return recurringPrice;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public SubscriptionTransitionType getTransitionType() {
        return type;
    }

    @Override
    public Long getTotalOrdering() {
        return totalOrdering;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DefaultBillingEvent");
        sb.append("{account=").append(account);
        sb.append(", billCycleDay=").append(billCycleDay);
        sb.append(", subscription=").append(subscription);
        sb.append(", effectiveDate=").append(effectiveDate);
        sb.append(", planPhase=").append(planPhase);
        sb.append(", plan=").append(plan);
        sb.append(", fixedPrice=").append(fixedPrice);
        sb.append(", recurringPrice=").append(recurringPrice);
        sb.append(", currency=").append(currency);
        sb.append(", description='").append(description).append('\'');
        sb.append(", billingModeType=").append(billingModeType);
        sb.append(", billingPeriod=").append(billingPeriod);
        sb.append(", type=").append(type);
        sb.append(", totalOrdering=").append(totalOrdering);
        sb.append(", timeZone=").append(timeZone);
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

        final DefaultBillingEvent that = (DefaultBillingEvent) o;

        if (billCycleDay != that.billCycleDay) {
            return false;
        }
        if (billingModeType != that.billingModeType) {
            return false;
        }
        if (billingPeriod != that.billingPeriod) {
            return false;
        }
        if (currency != that.currency) {
            return false;
        }
        if (!description.equals(that.description)) {
            return false;
        }
        if (!effectiveDate.equals(that.effectiveDate)) {
            return false;
        }
        if (fixedPrice != null ? !fixedPrice.equals(that.fixedPrice) : that.fixedPrice != null) {
            return false;
        }
        if (!plan.equals(that.plan)) {
            return false;
        }
        if (!planPhase.equals(that.planPhase)) {
            return false;
        }
        if (recurringPrice != null ? !recurringPrice.equals(that.recurringPrice) : that.recurringPrice != null) {
            return false;
        }
        if (!subscription.equals(that.subscription)) {
            return false;
        }
        if (!totalOrdering.equals(that.totalOrdering)) {
            return false;
        }
        if (type != that.type) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = billCycleDay.hashCode();
        result = 31 * result + subscription.hashCode();
        result = 31 * result + effectiveDate.hashCode();
        result = 31 * result + planPhase.hashCode();
        result = 31 * result + plan.hashCode();
        result = 31 * result + (fixedPrice != null ? fixedPrice.hashCode() : 0);
        result = 31 * result + (recurringPrice != null ? recurringPrice.hashCode() : 0);
        result = 31 * result + currency.hashCode();
        result = 31 * result + description.hashCode();
        result = 31 * result + billingModeType.hashCode();
        result = 31 * result + billingPeriod.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + totalOrdering.hashCode();
        return result;
    }

    @Override
    public DateTimeZone getTimeZone() {
        return timeZone;
    }
}
