package com.subsense.currency;

import java.util.Currency;
import java.util.Locale;

/** Currencies a subscription can be billed in. Add a constant to support another one. */
public enum CurrencyCode {
  GBP(Locale.UK, 3),
  INR(Locale.of("en", "IN"), 2), // ₹1,23,456.00: groups of two after the first three digits
  USD(Locale.US, 3);

  private final Locale displayLocale;
  private final int laterGroupSize;

  CurrencyCode(Locale displayLocale, int laterGroupSize) {
    this.displayLocale = displayLocale;
    this.laterGroupSize = laterGroupSize;
  }

  public String symbol() {
    return Currency.getInstance(name()).getSymbol(displayLocale);
  }

  /** Digits per group after the first group of three (3 for 1,234,567; 2 for 12,34,567). */
  public int laterGroupSize() {
    return laterGroupSize;
  }
}
