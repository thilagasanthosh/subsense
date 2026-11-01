package com.subsense.dashboard;

import com.subsense.common.MoneyView;
import com.subsense.currency.CurrencyCode;
import com.subsense.subscription.SubscriptionView;
import java.time.LocalDate;
import java.util.List;

/** Dashboard figures, all totals in the reporting currency. */
public record DashboardSummary(
    CurrencyCode reportingCurrency,
    LocalDate fxRatesAsOf,
    MoneyView monthlySpend,
    MoneyView annualSpend,
    int activeCount,
    int renewalsNext30Days,
    List<CategorySpend> spendByCategory,
    List<SubscriptionView> needsDecision) {

  public record CategorySpend(String category, String colour, MoneyView monthlySpend) {}
}
