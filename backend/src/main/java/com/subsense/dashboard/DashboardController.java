package com.subsense.dashboard;

import com.subsense.common.SubSenseProperties;
import com.subsense.currency.FxRateService;
import com.subsense.subscription.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard")
class DashboardController {

  private final SubscriptionService subscriptions;
  private final FxRateService fxRateService;
  private final DashboardService dashboardService;
  private final SubSenseProperties properties;
  private final Clock clock;

  DashboardController(
      SubscriptionService subscriptions,
      FxRateService fxRateService,
      DashboardService dashboardService,
      SubSenseProperties properties,
      Clock clock) {
    this.subscriptions = subscriptions;
    this.fxRateService = fxRateService;
    this.dashboardService = dashboardService;
    this.properties = properties;
    this.clock = clock;
  }

  @GetMapping("/summary")
  @Operation(summary = "Spend totals, category split and renewals needing a decision")
  DashboardSummary summary() {
    return dashboardService.summarise(
        subscriptions.findAllActive(),
        fxRateService.current(),
        properties.reportingCurrency(),
        LocalDate.now(clock));
  }
}
