package com.subsense.subscription;

import static com.subsense.currency.CurrencyCode.GBP;
import static com.subsense.currency.CurrencyCode.INR;
import static org.assertj.core.api.Assertions.assertThat;

import com.subsense.currency.Money;
import org.junit.jupiter.api.Test;

class BillingCycleTest {

  @Test
  void monthlyPriceIsItsOwnMonthlyEquivalent() {
    assertThat(BillingCycle.MONTHLY.monthlyEquivalent(Money.of("18.00", GBP)).rounded())
        .isEqualTo(Money.of("18.00", GBP));
  }

  @Test
  void quarterlyPriceIsSpreadOverThreeMonths() {
    assertThat(BillingCycle.QUARTERLY.monthlyEquivalent(Money.of("43.50", GBP)).rounded())
        .isEqualTo(Money.of("14.50", GBP));
  }

  @Test
  void yearlyPriceIsSpreadOverTwelveMonthsInItsOwnCurrency() {
    assertThat(BillingCycle.YEARLY.monthlyEquivalent(Money.of("1499.00", INR)).rounded())
        .isEqualTo(Money.of("124.92", INR));
  }
}
