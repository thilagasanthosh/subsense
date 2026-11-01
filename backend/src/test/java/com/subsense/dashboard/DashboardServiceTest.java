package com.subsense.dashboard;

import static com.subsense.currency.CurrencyCode.GBP;
import static com.subsense.currency.CurrencyCode.INR;
import static com.subsense.currency.CurrencyCode.USD;
import static com.subsense.support.SubscriptionBuilder.TODAY;
import static com.subsense.support.SubscriptionBuilder.aSubscription;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.subsense.common.MoneyView;
import com.subsense.currency.ExchangeRates;
import com.subsense.subscription.Category;
import com.subsense.subscription.Decision;
import com.subsense.subscription.Subscription;
import com.subsense.subscription.SubscriptionView;
import com.subsense.support.SubscriptionBuilder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {

  private static final ExchangeRates RATES =
      new ExchangeRates(
          LocalDate.of(2026, 10, 30),
          Map.of(
              GBP, new BigDecimal("0.85"),
              INR, new BigDecimal("90"),
              USD, new BigDecimal("1.10")));

  private static final Category AI = new Category("AI Tools", "#6366F1");
  private static final Category FUN = new Category("Entertainment", "#EC4899");

  private final DashboardService service = new DashboardService();

  @Test
  void totalsSpendAcrossCurrenciesInTheReportingCurrency() {
    // £10.00 + ₹900 (= €10 = £8.50) + $11 (= €10 = £8.50) = £27.00 a month
    List<Subscription> subscriptions =
        List.of(
            aSubscription().costing("10.00", GBP).build(),
            aSubscription().costing("900.00", INR).build(),
            aSubscription().costing("11.00", USD).build());

    DashboardSummary summary = service.summarise(subscriptions, RATES, GBP, TODAY);

    assertThat(summary.monthlySpend()).isEqualTo(new MoneyView("27.00", GBP));
    assertThat(summary.annualSpend()).isEqualTo(new MoneyView("324.00", GBP));
    assertThat(summary.fxRatesAsOf()).isEqualTo(LocalDate.of(2026, 10, 30));
  }

  @Test
  void leavesOutSubscriptionsBeingCancelled() {
    List<Subscription> subscriptions =
        List.of(
            aSubscription().costing("10.00", GBP).build(),
            aSubscription().costing("99.00", GBP).decided(Decision.CANCEL).build());

    DashboardSummary summary = service.summarise(subscriptions, RATES, GBP, TODAY);

    assertThat(summary.monthlySpend()).isEqualTo(new MoneyView("10.00", GBP));
    assertThat(summary.activeCount()).isEqualTo(1);
  }

  @Test
  void excludesCancelledFromSpendAndCounts() {
    Subscription cancelled =
        aSubscription().costing("99.00", GBP).renewingOn(TODAY).decided(Decision.CANCEL).build();
    cancelled.rollOver(TODAY, SubscriptionBuilder.NOW);
    List<Subscription> subscriptions =
        List.of(aSubscription().costing("10.00", GBP).build(), cancelled);

    DashboardSummary summary = service.summarise(subscriptions, RATES, GBP, TODAY);

    assertThat(summary.monthlySpend()).isEqualTo(new MoneyView("10.00", GBP));
    assertThat(summary.activeCount()).isEqualTo(1);
    assertThat(summary.renewalsNext30Days()).isEqualTo(1);
  }

  @Test
  void splitsSpendByCategoryLargestFirst() {
    List<Subscription> subscriptions =
        List.of(
            aSubscription().inCategory(AI).costing("18.00", GBP).build(),
            aSubscription().inCategory(FUN).costing("10.99", GBP).build(),
            aSubscription().inCategory(FUN).costing("12.99", GBP).build());

    DashboardSummary summary = service.summarise(subscriptions, RATES, GBP, TODAY);

    assertThat(summary.spendByCategory())
        .extracting(DashboardSummary.CategorySpend::category, c -> c.monthlySpend().amount())
        .containsExactly(tuple("Entertainment", "23.98"), tuple("AI Tools", "18.00"));
  }

  @Test
  void listsPendingRenewalsInTheNextFourteenDaysAsNeedingADecision() {
    List<Subscription> subscriptions =
        List.of(
            aSubscription().named("Soon").renewingOn(TODAY.plusDays(5)).build(),
            aSubscription()
                .named("Decided")
                .renewingOn(TODAY.plusDays(5))
                .decided(Decision.RENEW)
                .build(),
            aSubscription().named("Later").renewingOn(TODAY.plusDays(20)).build());

    DashboardSummary summary = service.summarise(subscriptions, RATES, GBP, TODAY);

    assertThat(summary.needsDecision()).extracting(SubscriptionView::name).containsExactly("Soon");
    assertThat(summary.renewalsNext30Days()).isEqualTo(3);
  }
}
