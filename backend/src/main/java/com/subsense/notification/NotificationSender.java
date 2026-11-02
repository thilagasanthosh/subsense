package com.subsense.notification;

/** Sends a push {@link Notification}. Implementations must be idempotent for the same payload. */
public interface NotificationSender {

  /**
   * Sends the notification and returns the number of attempts made (≥ 1).
   *
   * @throws NotificationFailedException if all retry attempts were exhausted
   */
  int send(Notification notification);
}
