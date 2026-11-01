package com.subsense.currency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An amount in a currency. Amounts keep full precision while calculating; call {@link #rounded()}
 * only for display and totals.
 */
public record Money(BigDecimal amount, CurrencyCode currency) {

  public Money {
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(currency, "currency");
  }

  public static Money zero(CurrencyCode currency) {
    return new Money(BigDecimal.ZERO, currency);
  }

  public static Money of(String amount, CurrencyCode currency) {
    return new Money(new BigDecimal(amount), currency);
  }

  public Money plus(Money other) {
    if (other.currency != currency) {
      throw new IllegalArgumentException(
          "Cannot add " + other.currency + " to " + currency + "; convert first");
    }
    return new Money(amount.add(other.amount), currency);
  }

  public Money times(int factor) {
    return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency);
  }

  public Money rounded() {
    return new Money(amount.setScale(2, RoundingMode.HALF_EVEN), currency);
  }
}
