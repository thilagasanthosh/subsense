package com.subsense.subscription;

import static com.subsense.currency.CurrencyCode.GBP;
import static com.subsense.currency.CurrencyCode.INR;
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

  @Test
  void setsAnchorDayFromRenewalDateOnCreate() {
    Subscription s = aSubscription().renewingOn(LocalDate.of(2027, 1, 31)).build();

    assertThat(s.getRenewalAnchorDay()).isEqualTo(31);
  }

  @Test
  void keepsAnchorDayWhenUpdateLeavesRenewalDateUnchanged() {
    Subscription s = aSubscription().renewingOn(LocalDate.of(2027, 1, 31)).build();
    s.rollOver(LocalDate.of(2027, 2, 1), NOW);

    s.update(
        aSubscription().named("Renamed").renewingOn(LocalDate.of(2027, 2, 28)).details(),
        s.getCategory(),
        NOW);

    assertThat(s.getRenewalAnchorDay()).isEqualTo(31);
  }

  @Test
  void resetsAnchorDayWhenUserChangesRenewalDate() {
    Subscription s = aSubscription().renewingOn(LocalDate.of(2027, 1, 31)).build();

    s.update(aSubscription().renewingOn(LocalDate.of(2027, 3, 15)).details(), s.getCategory(), NOW);

    assertThat(s.getRenewalAnchorDay()).isEqualTo(15);
  }

  @Test
  void renewDecisionAdvancesDateAndResetsToPending() {
    Subscription s =
        aSubscription()
            .monthly()
            .renewingOn(LocalDate.of(2026, 10, 31))
            .decided(Decision.RENEW)
            .build();

    s.rollOver(TODAY, NOW);

    assertThat(s.getNextRenewalDate()).isEqualTo(LocalDate.of(2026, 11, 30));
    assertThat(s.getDecision()).isEqualTo(Decision.PENDING);
  }

  @Test
  void pendingWithAutoRenewAdvancesDate() {
    Subscription s = aSubscription().monthly().renewingOn(LocalDate.of(2026, 10, 15)).build();

    s.rollOver(TODAY, NOW);

    assertThat(s.getNextRenewalDate()).isEqualTo(LocalDate.of(2026, 11, 15));
    assertThat(s.status()).isEqualTo(SubscriptionStatus.ACTIVE);
  }

  @Test
  void pendingWithoutAutoRenewLapsesToCancelled() {
    LocalDate renewal = LocalDate.of(2026, 10, 15);
    Subscription s = aSubscription().renewingOn(renewal).notAutoRenewing().build();

    s.rollOver(TODAY, NOW);

    assertThat(s.status()).isEqualTo(SubscriptionStatus.CANCELLED);
    assertThat(s.getCancelledOn()).isEqualTo(renewal);
    assertThat(s.getNextRenewalDate()).isEqualTo(renewal);
  }

  @Test
  void cancelDecisionBecomesCancelledOnRenewalDate() {
    Subscription s = aSubscription().renewingOn(TODAY).decided(Decision.CANCEL).build();

    s.rollOver(TODAY, NOW);

    assertThat(s.status()).isEqualTo(SubscriptionStatus.CANCELLED);
    assertThat(s.getCancelledOn()).isEqualTo(TODAY);
    assertThat(s.getNextRenewalDate()).isEqualTo(TODAY);
  }

  @Test
  void downgradeAppliesNewAmountAndClearsDowngrade() {
    Subscription s =
        aSubscription()
            .costing("700.00", INR)
            .renewingOn(LocalDate.of(2026, 10, 6))
            .downgradingTo("199.00")
            .build();

    s.rollOver(TODAY, NOW);

    assertThat(s.price()).isEqualTo(Money.of("199.00", INR));
    assertThat(s.getDowngradeAmount()).isNull();
    assertThat(s.getDecision()).isEqualTo(Decision.PENDING);
    assertThat(s.getNextRenewalDate()).isEqualTo(LocalDate.of(2026, 11, 6));
  }

  @Test
  void freeTrialBecomesPaidAtPriceAfterTrial() {
    Subscription s =
        aSubscription()
            .monthly()
            .freeTrialThen("29.99")
            .renewingOn(LocalDate.of(2026, 10, 31))
            .build();

    s.rollOver(TODAY, NOW);

    assertThat(s.getType()).isEqualTo(SubscriptionType.PAID);
    assertThat(s.status()).isEqualTo(SubscriptionStatus.ACTIVE);
    assertThat(s.price()).isEqualTo(Money.of("29.99", GBP));
    assertThat(s.getPriceAfterTrial()).isNull();
    assertThat(s.getNextRenewalDate()).isEqualTo(LocalDate.of(2026, 11, 30));
  }

  @Test
  void freeTrialWithDowngradeBecomesPaidAtDowngradeAmount() {
    Subscription s =
        aSubscription()
            .freeTrialThen("29.99")
            .renewingOn(LocalDate.of(2026, 10, 31))
            .downgradingTo("19.99")
            .build();

    s.rollOver(TODAY, NOW);

    assertThat(s.getType()).isEqualTo(SubscriptionType.PAID);
    assertThat(s.price()).isEqualTo(Money.of("19.99", GBP));
  }

  @Test
  void cancelledFreeTrialIsNeverCharged() {
    Subscription s =
        aSubscription().freeTrialThen("29.99").renewingOn(TODAY).decided(Decision.CANCEL).build();

    s.rollOver(TODAY, NOW);

    assertThat(s.status()).isEqualTo(SubscriptionStatus.CANCELLED);
    assertThat(s.price()).isEqualTo(Money.of("0", GBP));
  }
}
