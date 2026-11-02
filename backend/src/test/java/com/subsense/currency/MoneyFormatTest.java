package com.subsense.currency;

import static com.subsense.currency.CurrencyCode.GBP;
import static com.subsense.currency.CurrencyCode.INR;
import static com.subsense.currency.CurrencyCode.USD;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MoneyFormatTest {

  @Test
  void formatsPoundsForUk() {
    assertThat(MoneyFormat.format(Money.of("18", GBP))).isEqualTo("£18.00");
  }

  @Test
  void formatsRupeesWithIndianGrouping() {
    assertThat(MoneyFormat.format(Money.of("123456", INR))).isEqualTo("₹1,23,456.00");
  }

  @Test
  void formatsDollars() {
    assertThat(MoneyFormat.format(Money.of("1499.5", USD))).isEqualTo("$1,499.50");
  }

  @Test
  void roundsHalfEvenOnlyWhenFormatting() {
    assertThat(MoneyFormat.format(Money.of("0.125", GBP))).isEqualTo("£0.12");
    assertThat(MoneyFormat.format(Money.of("0.135", GBP))).isEqualTo("£0.14");
  }
}
