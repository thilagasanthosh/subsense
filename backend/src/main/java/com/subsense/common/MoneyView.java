package com.subsense.common;

import com.subsense.currency.CurrencyCode;
import com.subsense.currency.Money;
import java.math.BigDecimal;

/** Money on the wire: the amount as a string, so clients never round through floating point. */
public record MoneyView(String amount, CurrencyCode currency) {

  public static MoneyView of(Money money) {
    Money rounded = money.rounded();
    return new MoneyView(rounded.amount().toPlainString(), rounded.currency());
  }

  public static MoneyView of(BigDecimal amount, CurrencyCode currency) {
    return amount == null ? null : of(new Money(amount, currency));
  }
}
