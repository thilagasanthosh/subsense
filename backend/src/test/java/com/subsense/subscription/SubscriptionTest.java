package com.subsense.subscription;

import static com.subsense.currency.CurrencyCode.GBP;
import static com.subsense.support.SubscriptionBuilder.NOW;
import static com.subsense.support.SubscriptionBuilder.TODAY;
import static com.subsense.support.SubscriptionBuilder.aSubscription;
import static org.assertj.core.api.Assertions.assertThat;

import com.subsense.currency.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SubscriptionTest {

  @Test
  void cancelByDateIsTheRenewalDateMinusTheNoticePeriod() {
    Subscription s =
        aSubscription().renewingOn(LocalDate.of(2026, 12, 1)).withNoticeDays(14).build();

    assertThat(s.cancelByDate()).isEqualTo(LocalDate.of(2026, 11, 17));
  }

  @Test
  void countsDaysUntilRenewalFromToday() {
    Subscription s = aSubscription().renewingOn(LocalDate.of(2026, 11, 8)).build();

    assertThat(s.daysUntilRenewal(TODAY)).isEqualTo(7);
    assertThat(s.renewsWithin(TODAY, 7)).isTrue();
    assertThat(s.renewsWithin(TODAY, 6)).isFalse();
  }

  @Test
  void annualCostOfAQuarterlySubscriptionIsFourPayments() {
    Subscription s = aSubscription().quarterly().costing("43.50", GBP).build();

    assertThat(s.annualCost().rounded()).isEqualTo(Money.of("174.00", GBP));
  }

  @Test
  void freeTrialIsCostedAtItsPriceAfterTheTrial() {
    Subscription s = aSubscription().monthly().freeTrialThen("29.99").build();

    assertThat(s.status()).isEqualTo(SubscriptionStatus.TRIAL);
    assertThat(s.monthlyEquivalent().rounded()).isEqualTo(Money.of("29.99", GBP));
  }

  @Test
  void paidSubscriptionIsActiveUntilACancelDecision() {
    Subscription s = aSubscription().build();
    assertThat(s.status()).isEqualTo(SubscriptionStatus.ACTIVE);

    s.decide(Decision.CANCEL, null, NOW);

    assertThat(s.status()).isEqualTo(SubscriptionStatus.CANCELLING);
  }

  @Test
  void keepsTheDowngradeAmountOnlyForADowngrade() {
    Subscription s = aSubscription().build();

    s.decide(Decision.DOWNGRADE, new BigDecimal("9.99"), NOW);
    assertThat(s.getDowngradeAmount()).isEqualByComparingTo("9.99");

    s.decide(Decision.RENEW, new BigDecimal("9.99"), NOW);
    assertThat(s.getDowngradeAmount()).isNull();
  }
}
