package com.subsense.currency;

import static com.subsense.currency.CurrencyCode.GBP;
import static com.subsense.currency.CurrencyCode.INR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

  @Test
  void addsAmountsInTheSameCurrency() {
    assertThat(Money.of("10.50", GBP).plus(Money.of("2.25", GBP)))
        .isEqualTo(Money.of("12.75", GBP));
  }

  @Test
  void refusesToAddDifferentCurrencies() {
    assertThatThrownBy(() -> Money.of("10", GBP).plus(Money.of("10", INR)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("convert first");
  }

  @Test
  void roundsHalfEvenToTwoPlaces() {
    assertThat(Money.of("2.125", GBP).rounded()).isEqualTo(Money.of("2.12", GBP));
    assertThat(Money.of("2.135", GBP).rounded()).isEqualTo(Money.of("2.14", GBP));
  }
}
