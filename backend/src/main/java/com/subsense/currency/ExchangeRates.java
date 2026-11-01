package com.subsense.currency;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.Map;

/**
 * Rates for one day, expressed per euro (the ECB reference base), so any pair converts through EUR:
 * amount / rate(from) * rate(to).
 */
public record ExchangeRates(LocalDate asOf, Map<CurrencyCode, BigDecimal> perEuro) {

  private static final MathContext PRECISION = MathContext.DECIMAL64;

  public ExchangeRates {
    perEuro = Map.copyOf(perEuro);
  }

  public Money convert(Money money, CurrencyCode target) {
    if (money.currency() == target) {
      return money;
    }
    BigDecimal inEuro = money.amount().divide(rateFor(money.currency()), PRECISION);
    return new Money(inEuro.multiply(rateFor(target), PRECISION), target);
  }

  private BigDecimal rateFor(CurrencyCode currency) {
    BigDecimal rate = perEuro.get(currency);
    if (rate == null) {
      throw new IllegalStateException("No exchange rate for " + currency);
    }
    return rate;
  }
}
