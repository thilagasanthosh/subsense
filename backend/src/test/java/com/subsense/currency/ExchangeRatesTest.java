package com.subsense.currency;

import static com.subsense.currency.CurrencyCode.GBP;
import static com.subsense.currency.CurrencyCode.INR;
import static com.subsense.currency.CurrencyCode.USD;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExchangeRatesTest {

  private final ExchangeRates rates =
      new ExchangeRates(
          LocalDate.of(2026, 10, 30),
          Map.of(
              GBP, new BigDecimal("0.85"),
              INR, new BigDecimal("90"),
              USD, new BigDecimal("1.10")));

  @Test
  void leavesTheSameCurrencyUnchanged() {
    assertThat(rates.convert(Money.of("18.00", GBP), GBP)).isEqualTo(Money.of("18.00", GBP));
  }

  @Test
  void convertsThroughTheEuroBase() {
    // ₹900 = €10 = £8.50
    assertThat(rates.convert(Money.of("900", INR), GBP).rounded()).isEqualTo(Money.of("8.50", GBP));
    // $11 = €10 = £8.50
    assertThat(rates.convert(Money.of("11", USD), GBP).rounded()).isEqualTo(Money.of("8.50", GBP));
  }

  @Test
  void convertsFromTheReportingCurrencyToo() {
    // £8.50 = €10 = ₹900
    assertThat(rates.convert(Money.of("8.50", GBP), INR).rounded())
        .isEqualTo(Money.of("900.00", INR));
  }
}
