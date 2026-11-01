package com.subsense.dashboard;

import com.subsense.common.MoneyView;
import com.subsense.currency.CurrencyCode;
import com.subsense.currency.ExchangeRates;
import com.subsense.currency.Money;
import com.subsense.subscription.Category;
import com.subsense.subscription.Decision;
import com.subsense.subscription.Subscription;
import com.subsense.subscription.SubscriptionStatus;
import com.subsense.subscription.SubscriptionView;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Works out dashboard figures. Spend covers subscriptions that will keep charging: anything not
 * being cancelled or already cancelled, with free trials counted at their price after the trial.
 */
@Service
public class DashboardService {

  static final int RENEWAL_WINDOW_DAYS = 30;
  static final int DECISION_WINDOW_DAYS = 14;

  public DashboardSummary summarise(
      List<Subscription> subscriptions,
      ExchangeRates rates,
      CurrencyCode reporting,
      LocalDate today) {

    List<Subscription> charging =
        subscriptions.stream()
            .filter(s -> s.status() != SubscriptionStatus.CANCELLING)
            .filter(s -> s.status() != SubscriptionStatus.CANCELLED)
            .toList();

    Money monthly = Money.zero(reporting);
    Map<String, Money> byCategory = new LinkedHashMap<>();
    Map<String, String> colours = new LinkedHashMap<>();
    for (Subscription s : charging) {
      Money converted = rates.convert(s.monthlyEquivalent(), reporting);
      monthly = monthly.plus(converted);
      Category category = s.getCategory();
      byCategory.merge(category.getName(), converted, Money::plus);
      colours.putIfAbsent(category.getName(), category.getColour());
    }

    List<DashboardSummary.CategorySpend> spendByCategory =
        byCategory.entrySet().stream()
            .sorted(
                Comparator.comparing((Map.Entry<String, Money> e) -> e.getValue().amount())
                    .reversed())
            .map(
                e ->
                    new DashboardSummary.CategorySpend(
                        e.getKey(), colours.get(e.getKey()), MoneyView.of(e.getValue())))
            .toList();

    List<SubscriptionView> needsDecision =
        charging.stream()
            .filter(s -> s.getDecision() == Decision.PENDING)
            .filter(s -> s.renewsWithin(today, DECISION_WINDOW_DAYS))
            .map(s -> SubscriptionView.of(s, today, rates, reporting))
            .toList();

    int renewalsSoon =
        (int) charging.stream().filter(s -> s.renewsWithin(today, RENEWAL_WINDOW_DAYS)).count();

    return new DashboardSummary(
        reporting,
        rates.asOf(),
        MoneyView.of(monthly),
        MoneyView.of(monthly.times(12)),
        charging.size(),
        renewalsSoon,
        spendByCategory,
        needsDecision);
  }
}
