package com.subsense.subscription;

import com.subsense.common.MoneyView;
import com.subsense.common.SubSenseProperties;
import com.subsense.currency.ExchangeRates;
import com.subsense.currency.FxRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subscriptions")
@Tag(name = "Subscriptions")
class SubscriptionController {

  private final SubscriptionService service;
  private final FxRateService fxRateService;
  private final SubSenseProperties properties;
  private final Clock clock;

  SubscriptionController(
      SubscriptionService service,
      FxRateService fxRateService,
      SubSenseProperties properties,
      Clock clock) {
    this.service = service;
    this.fxRateService = fxRateService;
    this.properties = properties;
    this.clock = clock;
  }

  @GetMapping
  @Operation(summary = "All subscriptions, soonest renewal first")
  List<SubscriptionView> list() {
    ExchangeRates rates = fxRateService.current();
    LocalDate today = LocalDate.now(clock);
    return service.findAllActive().stream().map(s -> view(s, today, rates)).toList();
  }

  @GetMapping("/{id}")
  SubscriptionView get(@PathVariable Long id) {
    return view(service.get(id));
  }

  @PostMapping
  ResponseEntity<SubscriptionView> create(@Valid @RequestBody SubscriptionDetails details) {
    Subscription created = service.create(details);
    return ResponseEntity.created(URI.create("/api/v1/subscriptions/" + created.getId()))
        .body(view(created));
  }

  @PutMapping("/{id}")
  SubscriptionView update(@PathVariable Long id, @Valid @RequestBody SubscriptionDetails details) {
    return view(service.update(id, details));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Soft delete: hidden everywhere but kept for history")
  ResponseEntity<Void> delete(@PathVariable Long id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/decisions")
  @Operation(summary = "Record a renew, cancel or downgrade decision for the next renewal")
  SubscriptionView decide(@PathVariable Long id, @Valid @RequestBody DecisionRequest request) {
    return view(service.decide(id, request, DecisionSource.UI));
  }

  @GetMapping("/{id}/decisions")
  @Operation(summary = "Decision history, newest first")
  List<DecisionView> decisions(@PathVariable Long id) {
    Subscription subscription = service.get(id);
    return service.history(id).stream().map(h -> DecisionView.of(h, subscription)).toList();
  }

  private SubscriptionView view(Subscription subscription) {
    return view(subscription, LocalDate.now(clock), fxRateService.current());
  }

  private SubscriptionView view(Subscription subscription, LocalDate today, ExchangeRates rates) {
    return SubscriptionView.of(subscription, today, rates, properties.reportingCurrency());
  }

  record DecisionView(
      Decision decision,
      MoneyView newAmount,
      String reason,
      DecisionSource source,
      Instant decidedAt) {

    static DecisionView of(DecisionHistory history, Subscription subscription) {
      return new DecisionView(
          history.getDecision(),
          MoneyView.of(history.getNewAmount(), subscription.price().currency()),
          history.getReason(),
          history.getSource(),
          history.getDecidedAt());
    }
  }
}
