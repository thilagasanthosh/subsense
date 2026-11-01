package com.subsense.subscription;

import com.subsense.common.ConflictException;
import com.subsense.common.FieldValidationException;
import com.subsense.common.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {

  private final SubscriptionRepository subscriptions;
  private final CategoryRepository categories;
  private final DecisionHistoryRepository decisionHistory;
  private final Clock clock;

  SubscriptionService(
      SubscriptionRepository subscriptions,
      CategoryRepository categories,
      DecisionHistoryRepository decisionHistory,
      Clock clock) {
    this.subscriptions = subscriptions;
    this.categories = categories;
    this.decisionHistory = decisionHistory;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<Subscription> findAllActive() {
    return subscriptions.findAllByDeletedAtIsNullOrderByNextRenewalDateAsc();
  }

  @Transactional(readOnly = true)
  public Subscription get(Long id) {
    return subscriptions
        .findByIdAndDeletedAtIsNull(id)
        .orElseThrow(() -> new NotFoundException("Subscription " + id + " not found"));
  }

  @Transactional
  public Subscription create(SubscriptionDetails details) {
    validate(details);
    return subscriptions.save(Subscription.create(details, category(details), now()));
  }

  @Transactional
  public Subscription update(Long id, SubscriptionDetails details) {
    validate(details);
    Subscription subscription = notCancelled(id);
    subscription.update(details, category(details), now());
    return subscription;
  }

  @Transactional
  public void delete(Long id) {
    get(id).delete(now());
  }

  @Transactional
  public Subscription decide(Long id, DecisionRequest request, DecisionSource source) {
    if (request.decision() == Decision.DOWNGRADE && request.newAmount() == null) {
      throw new FieldValidationException("newAmount", "is required when downgrading");
    }
    Subscription subscription = notCancelled(id);
    Instant now = now();
    subscription.decide(request.decision(), request.newAmount(), now);
    decisionHistory.save(
        new DecisionHistory(
            id, request.decision(), request.newAmount(), request.reason(), source, now));
    return subscription;
  }

  @Transactional(readOnly = true)
  public List<DecisionHistory> history(Long id) {
    get(id);
    return decisionHistory.findBySubscriptionIdOrderByDecidedAtDesc(id);
  }

  /** Cancelled subscriptions are kept for reports but can no longer be changed. */
  private Subscription notCancelled(Long id) {
    Subscription subscription = get(id);
    if (subscription.status() == SubscriptionStatus.CANCELLED) {
      throw new ConflictException("Subscription is cancelled");
    }
    return subscription;
  }

  private static void validate(SubscriptionDetails details) {
    if (details.nextRenewalDate().isBefore(details.startDate())) {
      throw new FieldValidationException("nextRenewalDate", "must be on or after the start date");
    }
    if (details.type() == SubscriptionType.FREE_TRIAL && details.priceAfterTrial() == null) {
      throw new FieldValidationException("priceAfterTrial", "is required for a free trial");
    }
  }

  private Category category(SubscriptionDetails details) {
    return categories
        .findById(details.categoryId())
        .orElseThrow(() -> new FieldValidationException("categoryId", "is not a known category"));
  }

  private Instant now() {
    return clock.instant();
  }
}
