package com.subsense.subscription;

import com.subsense.currency.Money;
import java.math.BigDecimal;
import java.math.MathContext;

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
}
