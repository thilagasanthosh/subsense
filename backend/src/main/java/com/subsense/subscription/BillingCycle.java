package com.subsense.subscription;

import com.subsense.currency.Money;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;

/** How often a subscription is billed. Each cycle owns its own cost maths. */
public enum BillingCycle {
  MONTHLY(1),
  QUARTERLY(3),
  YEARLY(12);

  private final int months;

  BillingCycle(int months) {
    this.months = months;
  }

  public Money monthlyEquivalent(Money price) {
    BigDecimal perMonth = price.amount().divide(BigDecimal.valueOf(months), MathContext.DECIMAL64);
    return new Money(perMonth, price.currency());
  }

  /**
   * The renewal one cycle later, on the anchor day of month where it exists and on the last day of
   * shorter months (31 Jan, 28 Feb, 31 Mar).
   */
  public LocalDate next(LocalDate renewal, int anchorDay) {
    LocalDate sameMonthNextCycle = renewal.plusMonths(months);
    return sameMonthNextCycle.withDayOfMonth(
        Math.min(anchorDay, sameMonthNextCycle.lengthOfMonth()));
  }
}
