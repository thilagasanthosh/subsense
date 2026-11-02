package com.subsense.notification;

import com.subsense.currency.Money;
import com.subsense.currency.MoneyFormat;
import com.subsense.subscription.Subscription;
import com.subsense.subscription.SubscriptionType;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Writes the push notification for a due reminder. No payment labels or notes (privacy). */
public class ReminderMessage {

  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK);

  private final String appUrl;

  public ReminderMessage(String appUrl) {
    this.appUrl = appUrl;
  }

  public Notification notificationFor(Subscription s, DueReminder due) {
    return new Notification(
        title(s, due),
        body(s),
        due.urgent(),
        appUrl + "/subscriptions/" + s.getId(),
        List.of("bell"));
  }

  private static String title(Subscription s, DueReminder due) {
    if (due.offsetDays() == ReminderPolicy.RENEWS_TODAY) {
      return s.getName() + " renews today";
    }
    String days = due.daysLeft() == 1 ? "1 day" : due.daysLeft() + " days";
    if (s.getNoticeDays() > 0) {
      return s.getName() + ": " + days + " left to cancel";
    }
    if (s.getType() == SubscriptionType.FREE_TRIAL) {
      return s.getName() + " trial ends " + (due.daysLeft() == 1 ? "tomorrow" : "in " + days);
    }
    return s.getName() + " renews " + (due.daysLeft() == 1 ? "tomorrow" : "in " + days);
  }

  private static String body(Subscription s) {
    String cycle = s.getBillingCycle().name().toLowerCase(Locale.ROOT);
    String price =
        s.getType() == SubscriptionType.FREE_TRIAL
            ? MoneyFormat.format(new Money(s.getPriceAfterTrial(), s.price().currency()))
                + " "
                + cycle
                + " after the trial"
            : MoneyFormat.format(s.price()) + " " + cycle;
    return price + " · cancel by " + DATE.format(s.cancelByDate()) + " · " + decision(s);
  }

  private static String decision(Subscription s) {
    return switch (s.getDecision()) {
      case PENDING -> "not decided yet";
      case RENEW -> "you chose to renew";
      case DOWNGRADE ->
          "you chose to downgrade to "
              + MoneyFormat.format(new Money(s.getDowngradeAmount(), s.price().currency()));
      case CANCEL -> "you chose to cancel";
    };
  }
}
