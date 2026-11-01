package com.subsense.subscription;

import com.subsense.common.MoneyView;
import com.subsense.currency.CurrencyCode;
import com.subsense.currency.ExchangeRates;
import java.time.LocalDate;

/** A subscription as the UI and API clients see it, with derived values already worked out. */
public record SubscriptionView(
    Long id,
    String name,
    String vendor,
    CategoryView category,
    SubscriptionType type,
    BillingCycle billingCycle,
    MoneyView price,
    MoneyView priceAfterTrial,
    LocalDate startDate,
    LocalDate nextRenewalDate,
    int noticeDays,
    LocalDate cancelByDate,
    long daysUntilRenewal,
    boolean autoRenew,
    String paymentLabel,
    String owner,
    SubscriptionStatus status,
    Decision decision,
    MoneyView downgradeAmount,
    UsageRating usageRating,
    String notes,
    MoneyView monthlyEquivalent,
    MoneyView monthlyEquivalentReporting,
    MoneyView annualCostReporting) {

  public record CategoryView(Long id, String name, String colour) {

    static CategoryView of(Category category) {
      return new CategoryView(category.getId(), category.getName(), category.getColour());
    }
  }

  public static SubscriptionView of(
      Subscription s, LocalDate today, ExchangeRates rates, CurrencyCode reporting) {
    CurrencyCode currency = s.price().currency();
    return new SubscriptionView(
        s.getId(),
        s.getName(),
        s.getVendor(),
        CategoryView.of(s.getCategory()),
        s.getType(),
        s.getBillingCycle(),
        MoneyView.of(s.price()),
        MoneyView.of(s.getPriceAfterTrial(), currency),
        s.getStartDate(),
        s.getNextRenewalDate(),
        s.getNoticeDays(),
        s.cancelByDate(),
        s.daysUntilRenewal(today),
        s.isAutoRenew(),
        s.getPaymentLabel(),
        s.getOwner(),
        s.status(),
        s.getDecision(),
        MoneyView.of(s.getDowngradeAmount(), currency),
        s.getUsageRating(),
        s.getNotes(),
        MoneyView.of(s.monthlyEquivalent()),
        MoneyView.of(rates.convert(s.monthlyEquivalent(), reporting)),
        MoneyView.of(rates.convert(s.annualCost(), reporting)));
  }
}
