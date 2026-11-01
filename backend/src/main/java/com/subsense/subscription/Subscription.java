package com.subsense.subscription;

import com.subsense.currency.CurrencyCode;
import com.subsense.currency.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "subscription")
public class Subscription {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String vendor;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "category_id")
  private Category category;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SubscriptionType type;

  @Enumerated(EnumType.STRING)
  @Column(name = "billing_cycle", nullable = false)
  private BillingCycle billingCycle;

  @Column(nullable = false)
  private BigDecimal amount;

  @Column(name = "price_after_trial")
  private BigDecimal priceAfterTrial;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CurrencyCode currency;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "next_renewal_date", nullable = false)
  private LocalDate nextRenewalDate;

  @Column(name = "notice_days", nullable = false)
  private int noticeDays;

  @Column(name = "auto_renew", nullable = false)
  private boolean autoRenew;

  @Column(name = "payment_label")
  private String paymentLabel;

  @Column(nullable = false)
  private String owner;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Decision decision = Decision.PENDING;

  @Column(name = "downgrade_amount")
  private BigDecimal downgradeAmount;

  @Enumerated(EnumType.STRING)
  @Column(name = "usage_rating")
  private UsageRating usageRating;

  private String notes;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected Subscription() {}

  /** Creates a subscription from validated details. */
  public static Subscription create(SubscriptionDetails details, Category category, Instant now) {
    Subscription subscription = new Subscription();
    subscription.createdAt = now;
    subscription.update(details, category, now);
    return subscription;
  }

  public void update(SubscriptionDetails details, Category category, Instant now) {
    this.name = details.name();
    this.vendor = details.vendor();
    this.category = category;
    this.type = details.type();
    this.billingCycle = details.billingCycle();
    this.amount = details.amount();
    this.priceAfterTrial = details.priceAfterTrial();
    this.currency = details.currency();
    this.startDate = details.startDate();
    this.nextRenewalDate = details.nextRenewalDate();
    this.noticeDays = details.noticeDays();
    this.autoRenew = details.autoRenew();
    this.paymentLabel = details.paymentLabel();
    this.owner = details.owner();
    this.usageRating = details.usageRating();
    this.notes = details.notes();
    this.updatedAt = now;
  }

  public void decide(Decision newDecision, BigDecimal newAmount, Instant now) {
    this.decision = newDecision;
    this.downgradeAmount = newDecision == Decision.DOWNGRADE ? newAmount : null;
    this.updatedAt = now;
  }

  public void delete(Instant now) {
    this.deletedAt = now;
    this.updatedAt = now;
  }

  public Money price() {
    return new Money(amount, currency);
  }

  /** The cost per month in the subscription's own currency (trials use the price after trial). */
  public Money monthlyEquivalent() {
    Money recurring = type == SubscriptionType.FREE_TRIAL ? priceAfterTrialMoney() : price();
    return billingCycle.monthlyEquivalent(recurring);
  }

  public Money annualCost() {
    return monthlyEquivalent().times(12);
  }

  /** The last day to cancel without being charged again. */
  public LocalDate cancelByDate() {
    return nextRenewalDate.minusDays(noticeDays);
  }

  public long daysUntilRenewal(LocalDate today) {
    return ChronoUnit.DAYS.between(today, nextRenewalDate);
  }

  public boolean renewsWithin(LocalDate today, int days) {
    long remaining = daysUntilRenewal(today);
    return remaining >= 0 && remaining <= days;
  }

  public SubscriptionStatus status() {
    if (decision == Decision.CANCEL) {
      return SubscriptionStatus.CANCELLING;
    }
    return type == SubscriptionType.FREE_TRIAL
        ? SubscriptionStatus.TRIAL
        : SubscriptionStatus.ACTIVE;
  }

  private Money priceAfterTrialMoney() {
    return new Money(priceAfterTrial, currency);
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getVendor() {
    return vendor;
  }

  public Category getCategory() {
    return category;
  }

  public SubscriptionType getType() {
    return type;
  }

  public BillingCycle getBillingCycle() {
    return billingCycle;
  }

  public BigDecimal getPriceAfterTrial() {
    return priceAfterTrial;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public LocalDate getNextRenewalDate() {
    return nextRenewalDate;
  }

  public int getNoticeDays() {
    return noticeDays;
  }

  public boolean isAutoRenew() {
    return autoRenew;
  }

  public String getPaymentLabel() {
    return paymentLabel;
  }

  public String getOwner() {
    return owner;
  }

  public Decision getDecision() {
    return decision;
  }

  public BigDecimal getDowngradeAmount() {
    return downgradeAmount;
  }

  public UsageRating getUsageRating() {
    return usageRating;
  }

  public String getNotes() {
    return notes;
  }
}
