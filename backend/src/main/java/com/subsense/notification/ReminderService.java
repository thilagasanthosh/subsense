package com.subsense.notification;

import com.subsense.subscription.Subscription;
import com.subsense.subscription.SubscriptionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Sends push reminders for subscriptions whose renewal is approaching. Each reminder is sent at
 * most once: a {@link NotificationLog} row claimed as SENDING prevents duplicates from concurrent
 * runs; a SENDING row left by a crash is not re-sent (at most-once guarantee). A FAILED row from a
 * previous run is re-attempted on the next run.
 */
@Service
public class ReminderService {

  private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

  private final SubscriptionRepository subscriptions;
  private final NotificationLogRepository logs;
  private final ReminderPolicy policy;
  private final ReminderMessage messages;
  private final Optional<NotificationSender> sender;
  private final Clock clock;
  private final TransactionOperations transactions;

  ReminderService(
      SubscriptionRepository subscriptions,
      NotificationLogRepository logs,
      ReminderPolicy policy,
      ReminderMessage messages,
      Optional<NotificationSender> sender,
      Clock clock,
      TransactionOperations transactions) {
    this.subscriptions = subscriptions;
    this.logs = logs;
    this.policy = policy;
    this.messages = messages;
    this.sender = sender;
    this.clock = clock;
    this.transactions = transactions;
  }

  public ReminderResult run() {
    if (sender.isEmpty()) {
      log.debug("Reminder job skipped: ntfy topic not configured");
      return new ReminderResult(0, 0, 0);
    }
    LocalDate today = LocalDate.now(clock);
    int sent = 0, failed = 0, skipped = 0;
    for (Subscription s : subscriptions.findAllEligibleForReminders()) {
      Optional<DueReminder> due = policy.due(s, today);
      if (due.isEmpty()) {
        continue;
      }
      try {
        String outcome = transactions.execute(tx -> attempt(s, due.get()));
        switch (outcome) {
          case NotificationLog.SENT -> sent++;
          case NotificationLog.FAILED -> failed++;
          default -> skipped++;
        }
      } catch (RuntimeException e) {
        failed++;
        log.warn("Reminder for '{}' failed unexpectedly: {}", s.getName(), e.getMessage(), e);
      }
    }
    log.info("Reminder run for {}: {} sent, {} failed, {} skipped", today, sent, failed, skipped);
    return new ReminderResult(sent, failed, skipped);
  }

  private String attempt(Subscription s, DueReminder due) {
    Optional<NotificationLog> existing =
        logs.findBySubscriptionIdAndRenewalDateAndOffsetDays(
            s.getId(), due.renewalDate(), due.offsetDays());

    if (existing.isPresent()) {
      NotificationLog entry = existing.get();
      if (NotificationLog.SENT.equals(entry.getStatus())
          || NotificationLog.SENDING.equals(entry.getStatus())) {
        return NotificationLog.SENDING; // "skipped"
      }
      // FAILED: re-attempt
      entry.markSending();
      logs.save(entry);
      return send(s, due, entry);
    }

    // No prior record: claim it
    NotificationLog entry =
        NotificationLog.sending(s.getId(), due.renewalDate(), due.offsetDays(), clock.instant());
    logs.save(entry);
    return send(s, due, entry);
  }

  private String send(Subscription s, DueReminder due, NotificationLog entry) {
    Notification notification = messages.notificationFor(s, due);
    try {
      int attempts = sender.get().send(notification);
      entry.markSent(attempts, clock.instant());
      logs.save(entry);
      log.info("Reminder sent for '{}' (offset {} days)", s.getName(), due.offsetDays());
      return NotificationLog.SENT;
    } catch (NotificationFailedException e) {
      entry.markFailed(e.getAttempts(), e.getMessage());
      logs.save(entry);
      log.warn("Reminder failed for '{}': {}", s.getName(), e.getMessage());
      return NotificationLog.FAILED;
    }
  }

  public record ReminderResult(int sent, int failed, int skipped) {}
}
