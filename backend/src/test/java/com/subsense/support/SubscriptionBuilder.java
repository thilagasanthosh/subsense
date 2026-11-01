package com.subsense.support;

import com.subsense.currency.CurrencyCode;
import com.subsense.subscription.BillingCycle;
import com.subsense.subscription.Category;
import com.subsense.subscription.Decision;
import com.subsense.subscription.Subscription;
import com.subsense.subscription.SubscriptionDetails;
import com.subsense.subscription.SubscriptionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Readable test data: {@code aSubscription().monthly().costing("18.00", GBP).build()}. */
public final class SubscriptionBuilder {

  public static final Instant NOW = Instant.parse("2026-11-01T09:00:00Z");
  public static final LocalDate TODAY = LocalDate.of(2026, 11, 1);

  private String name = "Claude Pro";
  private String vendor = "Anthropic";
  private Long categoryId = 1L;
  private Category category = new Category("AI Tools", "#6366F1");
  private SubscriptionType type = SubscriptionType.PAID;
  private BillingCycle cycle = BillingCycle.MONTHLY;
  private BigDecimal amount = new BigDecimal("18.00");
  private BigDecimal priceAfterTrial;
  private CurrencyCode currency = CurrencyCode.GBP;
  private LocalDate startDate = LocalDate.of(2026, 1, 15);
  private LocalDate nextRenewalDate = LocalDate.of(2026, 11, 15);
  private int noticeDays;
  private String paymentLabel = "Visa";
  private Decision decision = Decision.PENDING;
  private BigDecimal downgradeAmount;
  private boolean autoRenew = true;

  private SubscriptionBuilder() {}

  public static SubscriptionBuilder aSubscription() {
    return new SubscriptionBuilder();
  }

  public SubscriptionBuilder named(String name) {
    this.name = name;
    return this;
  }

  public SubscriptionBuilder inCategory(Category category) {
    this.category = category;
    return this;
  }

  public SubscriptionBuilder withCategoryId(Long categoryId) {
    this.categoryId = categoryId;
    return this;
  }

  public SubscriptionBuilder monthly() {
    this.cycle = BillingCycle.MONTHLY;
    return this;
  }

  public SubscriptionBuilder quarterly() {
    this.cycle = BillingCycle.QUARTERLY;
    return this;
  }

  public SubscriptionBuilder yearly() {
    this.cycle = BillingCycle.YEARLY;
    return this;
  }

  public SubscriptionBuilder costing(String amount, CurrencyCode currency) {
    this.amount = new BigDecimal(amount);
    this.currency = currency;
    return this;
  }

  public SubscriptionBuilder freeTrialThen(String priceAfterTrial) {
    this.type = SubscriptionType.FREE_TRIAL;
    this.amount = BigDecimal.ZERO;
    this.priceAfterTrial = priceAfterTrial == null ? null : new BigDecimal(priceAfterTrial);
    return this;
  }

  public SubscriptionBuilder startingOn(LocalDate startDate) {
    this.startDate = startDate;
    return this;
  }

  public SubscriptionBuilder renewingOn(LocalDate nextRenewalDate) {
    this.nextRenewalDate = nextRenewalDate;
    return this;
  }

  public SubscriptionBuilder withNoticeDays(int noticeDays) {
    this.noticeDays = noticeDays;
    return this;
  }

  public SubscriptionBuilder withPaymentLabel(String paymentLabel) {
    this.paymentLabel = paymentLabel;
    return this;
  }

  public SubscriptionBuilder decided(Decision decision) {
    this.decision = decision;
    return this;
  }

  public SubscriptionBuilder downgradingTo(String newAmount) {
    this.decision = Decision.DOWNGRADE;
    this.downgradeAmount = new BigDecimal(newAmount);
    return this;
  }

  public SubscriptionBuilder notAutoRenewing() {
    this.autoRenew = false;
    return this;
  }

  public SubscriptionDetails details() {
    return new SubscriptionDetails(
        name,
        vendor,
        categoryId,
        type,
        cycle,
        amount,
        priceAfterTrial,
        currency,
        startDate,
        nextRenewalDate,
        noticeDays,
        autoRenew,
        paymentLabel,
        "Me",
        null,
        null);
  }

  public Subscription build() {
    Subscription subscription = Subscription.create(details(), category, NOW);
    if (decision != Decision.PENDING) {
      subscription.decide(decision, downgradeAmount, NOW);
    }
    return subscription;
  }
}
