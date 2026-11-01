package com.subsense.subscription;

import static com.subsense.currency.CurrencyCode.GBP;
import static com.subsense.currency.CurrencyCode.INR;
import static org.assertj.core.api.Assertions.assertThat;

import com.subsense.currency.Money;
import java.time.LocalDate;
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

  @Test
  void advancesMonthlyRenewalBySameDayOfMonth() {
    assertThat(BillingCycle.MONTHLY.next(LocalDate.of(2026, 11, 15), 15))
        .isEqualTo(LocalDate.of(2026, 12, 15));
  }

  @Test
  void advancesMonthlyRenewalFrom31JanTo28Feb() {
    assertThat(BillingCycle.MONTHLY.next(LocalDate.of(2027, 1, 31), 31))
        .isEqualTo(LocalDate.of(2027, 2, 28));
  }

  @Test
  void advancesMonthlyRenewalFrom31JanTo29FebInLeapYear() {
    assertThat(BillingCycle.MONTHLY.next(LocalDate.of(2028, 1, 31), 31))
        .isEqualTo(LocalDate.of(2028, 2, 29));
  }

  @Test
  void returnsToAnchorDay31InMarchAfterFebruary() {
    assertThat(BillingCycle.MONTHLY.next(LocalDate.of(2027, 2, 28), 31))
        .isEqualTo(LocalDate.of(2027, 3, 31));
  }

  @Test
  void advancesQuarterlyRenewalFrom30NovTo28FebThen30May() {
    LocalDate february = BillingCycle.QUARTERLY.next(LocalDate.of(2026, 11, 30), 30);
    assertThat(february).isEqualTo(LocalDate.of(2027, 2, 28));
    assertThat(BillingCycle.QUARTERLY.next(february, 30)).isEqualTo(LocalDate.of(2027, 5, 30));
  }

  @Test
  void advancesYearlyRenewalFrom29FebTo28FebInNonLeapYear() {
    assertThat(BillingCycle.YEARLY.next(LocalDate.of(2028, 2, 29), 29))
        .isEqualTo(LocalDate.of(2029, 2, 28));
  }

  @Test
  void returnsYearlyRenewalTo29FebInNextLeapYear() {
    assertThat(BillingCycle.YEARLY.next(LocalDate.of(2031, 2, 28), 29))
        .isEqualTo(LocalDate.of(2032, 2, 29));
  }

  @Test
  void advancesDecemberRenewalIntoNextYear() {
    assertThat(BillingCycle.MONTHLY.next(LocalDate.of(2026, 12, 31), 31))
        .isEqualTo(LocalDate.of(2027, 1, 31));
  }
}
