package com.subsense.subscription;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Applies the F-04 rules to every subscription whose renewal date has arrived. Safe to run more
 * than once a day: after a run nothing is due until the next renewal date.
 */
@Service
public class RenewalRolloverService {

  private static final Logger log = LoggerFactory.getLogger(RenewalRolloverService.class);

  private final SubscriptionRepository subscriptions;
  private final DecisionHistoryRepository decisionHistory;
  private final TransactionOperations transactions;
  private final Clock clock;

  RenewalRolloverService(
      SubscriptionRepository subscriptions,
      DecisionHistoryRepository decisionHistory,
      TransactionOperations transactions,
      Clock clock) {
    this.subscriptions = subscriptions;
    this.decisionHistory = decisionHistory;
    this.transactions = transactions;
    this.clock = clock;
  }

  /** Returns how many subscriptions changed. */
  public int run() {
    LocalDate today = LocalDate.now(clock);
    int changed = 0;
    int failed = 0;
    for (Long id : subscriptions.findIdsDueForRollover(today)) {
      try {
        // One transaction per subscription, so one failure never blocks the others.
        if (Boolean.TRUE.equals(transactions.execute(status -> rollOver(id, today)))) {
          changed++;
        }
      } catch (RuntimeException e) {
        failed++;
        log.warn("Renewal rollover failed for subscription {}; retrying next run", id, e);
      }
    }
    log.info("Renewal rollover for {}: {} changed, {} failed", today, changed, failed);
    return changed;
  }

  private boolean rollOver(Long id, LocalDate today) {
    Subscription subscription = subscriptions.findById(id).orElseThrow();
    boolean rolledOver = false;
    Optional<RolloverOutcome> outcome;
    // Loops to catch up when the app was off for more than one cycle.
    while ((outcome = subscription.rollOver(today, clock.instant())).isPresent()) {
      record(id, outcome.get());
      rolledOver = true;
    }
    return rolledOver;
  }

  private void record(Long subscriptionId, RolloverOutcome outcome) {
    decisionHistory.save(
        new DecisionHistory(
            subscriptionId,
            outcome.decision(),
            outcome.newAmount(),
            outcome.reason(),
            DecisionSource.SYSTEM,
            clock.instant()));
  }
}
