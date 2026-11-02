package com.subsense.notification;

import com.subsense.subscription.Decision;
import com.subsense.subscription.Subscription;
import com.subsense.subscription.SubscriptionStatus;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Which reminder, if any, is due for a subscription today. Pure: today is a parameter.
 *
 * <p>Countdown reminders (e.g. 14, 7, 1 days before the cancel-by date) only while nothing has been
 * decided; a "renews today" confirmation (offset 0) unless the decision is to cancel.
 */
public class ReminderPolicy {

  static final int RENEWS_TODAY = 0;

  private final List<Integer> offsets;

  public ReminderPolicy(List<Integer> offsets) {
    if (offsets.isEmpty() || offsets.stream().anyMatch(o -> o < 1 || o > 365)) {
      throw new IllegalArgumentException(
          "Reminder offsets must be between 1 and 365 days, got " + offsets);
    }
    this.offsets = offsets.stream().distinct().sorted().toList();
  }

  public Optional<DueReminder> due(Subscription subscription, LocalDate today) {
    SubscriptionStatus status = subscription.status();
    if (status != SubscriptionStatus.TRIAL && status != SubscriptionStatus.ACTIVE) {
      return Optional.empty();
    }
    LocalDate renewal = subscription.getNextRenewalDate();
    if (renewal.equals(today)) {
      return Optional.of(new DueReminder(renewal, RENEWS_TODAY, 0));
    }
    if (subscription.getDecision() != Decision.PENDING) {
      return Optional.empty();
    }
    long daysLeft = ChronoUnit.DAYS.between(today, subscription.cancelByDate());
    if (daysLeft < 1) {
      return Optional.empty();
    }
    // The smallest offset not yet passed, so a missed day still gets its (late) reminder.
    return offsets.stream()
        .filter(offset -> offset >= daysLeft)
        .findFirst()
        .map(offset -> new DueReminder(renewal, offset, daysLeft));
  }
}
