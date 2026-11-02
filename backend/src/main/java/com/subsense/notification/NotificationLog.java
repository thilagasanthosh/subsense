package com.subsense.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/** Tracks which push reminders have been sent, so each is sent exactly once. */
@Entity
@Table(name = "notification_log")
public class NotificationLog {

  public static final String SENDING = "SENDING";
  public static final String SENT = "SENT";
  public static final String FAILED = "FAILED";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "subscription_id", nullable = false)
  private Long subscriptionId;

  @Column(name = "renewal_date", nullable = false)
  private LocalDate renewalDate;

  @Column(name = "offset_days", nullable = false)
  private int offsetDays;

  @Column(nullable = false, length = 10)
  private String status;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "last_error", length = 500)
  private String lastError;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected NotificationLog() {}

  private NotificationLog(
      Long subscriptionId,
      LocalDate renewalDate,
      int offsetDays,
      String status,
      int attempts,
      String lastError,
      Instant sentAt,
      Instant now) {
    this.subscriptionId = subscriptionId;
    this.renewalDate = renewalDate;
    this.offsetDays = offsetDays;
    this.status = status;
    this.attempts = attempts;
    this.lastError = lastError;
    this.sentAt = sentAt;
    this.createdAt = now;
  }

  public static NotificationLog sending(
      Long subscriptionId, LocalDate renewalDate, int offsetDays, Instant now) {
    return new NotificationLog(
        subscriptionId, renewalDate, offsetDays, SENDING, 0, null, null, now);
  }

  public static NotificationLog sent(
      Long subscriptionId, LocalDate renewalDate, int offsetDays, int attempts, Instant now) {
    return new NotificationLog(
        subscriptionId, renewalDate, offsetDays, SENT, attempts, null, now, now);
  }

  public static NotificationLog failed(
      Long subscriptionId,
      LocalDate renewalDate,
      int offsetDays,
      int attempts,
      String error,
      Instant now) {
    return new NotificationLog(
        subscriptionId, renewalDate, offsetDays, FAILED, attempts, error, null, now);
  }

  public Long getId() {
    return id;
  }

  public String getStatus() {
    return status;
  }

  public int getAttempts() {
    return attempts;
  }

  public void markSending() {
    this.status = SENDING;
    this.lastError = null;
  }

  public void markSent(int attempts, Instant now) {
    this.status = SENT;
    this.attempts = attempts;
    this.sentAt = now;
    this.lastError = null;
  }

  public void markFailed(int attempts, String error) {
    this.status = FAILED;
    this.attempts = attempts;
    this.lastError = error != null && error.length() > 500 ? error.substring(0, 500) : error;
  }
}
