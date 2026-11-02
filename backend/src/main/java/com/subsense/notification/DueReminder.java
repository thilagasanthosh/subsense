package com.subsense.notification;

import java.time.LocalDate;

/**
 * A reminder that should go out today. {@code offsetDays} is the configured offset it counts as (0
 * = "renews today"); {@code daysLeft} is the real number of days to the cancel-by date.
 */
public record DueReminder(LocalDate renewalDate, int offsetDays, long daysLeft) {

  /** One day left, or renewing today: shown with high priority on the phone. */
  public boolean urgent() {
    return offsetDays <= 1;
  }
}
