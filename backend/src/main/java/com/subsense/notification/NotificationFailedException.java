package com.subsense.notification;

/** Thrown when a notification could not be delivered after all retry attempts. */
public class NotificationFailedException extends RuntimeException {

  private final int attempts;

  public NotificationFailedException(String message, int attempts) {
    super(message);
    this.attempts = attempts;
  }

  public int getAttempts() {
    return attempts;
  }
}
