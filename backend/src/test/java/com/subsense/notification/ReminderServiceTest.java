package com.subsense.notification;

import static com.subsense.support.SubscriptionBuilder.NOW;
import static com.subsense.support.SubscriptionBuilder.TODAY;
import static com.subsense.support.SubscriptionBuilder.aSubscription;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.subsense.subscription.Subscription;
import com.subsense.subscription.SubscriptionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;

class ReminderServiceTest {

  private static final ZoneId LONDON = ZoneId.of("Europe/London");
  private static final LocalDate IN_7 = TODAY.plusDays(7);

  private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
  private final NotificationLogRepository logs = mock(NotificationLogRepository.class);
  private final NotificationSender sender = mock(NotificationSender.class);
  private final ReminderPolicy policy = new ReminderPolicy(List.of(14, 7, 1));
  private final ReminderMessage messages = new ReminderMessage("https://app.example");

  private ReminderService service(Clock clock) {
    return new ReminderService(
        subscriptions,
        logs,
        policy,
        messages,
        Optional.of(sender),
        clock,
        TransactionOperations.withoutTransaction());
  }

  private final ReminderService service = service(Clock.fixed(NOW, LONDON));

  private Subscription claudePro() {
    return aSubscription().named("Claude Pro").renewingOn(IN_7).build();
  }

  @Test
  void sendsDueReminderAndLogsSent() {
    Subscription s = claudePro();
    when(subscriptions.findAllEligibleForReminders()).thenReturn(List.of(s));
    when(logs.findBySubscriptionIdAndRenewalDateAndOffsetDays(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(sender.send(any())).thenReturn(1);

    ReminderService.ReminderResult result = service.run();

    assertThat(result.sent()).isEqualTo(1);
    assertThat(result.failed()).isZero();
    assertThat(result.skipped()).isZero();
    verify(sender).send(argThat(n -> n != null && n.title().contains("Claude Pro")));
    // Two saves: one to claim SENDING, one to mark SENT on the same mutable object
    verify(logs, org.mockito.Mockito.times(2)).save(any());
  }

  @Test
  void skipsReminderAlreadySent() {
    Subscription s = claudePro();
    when(subscriptions.findAllEligibleForReminders()).thenReturn(List.of(s));
    NotificationLog existing = NotificationLog.sent(s.getId(), IN_7, 7, 1, NOW);
    when(logs.findBySubscriptionIdAndRenewalDateAndOffsetDays(any(), any(), any()))
        .thenReturn(Optional.of(existing));

    ReminderService.ReminderResult result = service.run();

    assertThat(result.skipped()).isEqualTo(1);
    assertThat(result.sent()).isZero();
    verify(sender, never()).send(any());
  }

  @Test
  void skipsReminderStillSending() {
    Subscription s = claudePro();
    when(subscriptions.findAllEligibleForReminders()).thenReturn(List.of(s));
    NotificationLog sending = NotificationLog.sending(s.getId(), IN_7, 7, NOW);
    when(logs.findBySubscriptionIdAndRenewalDateAndOffsetDays(any(), any(), any()))
        .thenReturn(Optional.of(sending));

    ReminderService.ReminderResult result = service.run();

    assertThat(result.skipped()).isEqualTo(1);
    verify(sender, never()).send(any());
  }

  @Test
  void resendsReminderThatFailedLastRun() {
    Subscription s = claudePro();
    when(subscriptions.findAllEligibleForReminders()).thenReturn(List.of(s));
    NotificationLog failed = NotificationLog.failed(s.getId(), IN_7, 7, 1, "timed out", NOW);
    when(logs.findBySubscriptionIdAndRenewalDateAndOffsetDays(any(), any(), any()))
        .thenReturn(Optional.of(failed));
    when(sender.send(any())).thenReturn(1);

    ReminderService.ReminderResult result = service.run();

    assertThat(result.sent()).isEqualTo(1);
    assertThat(result.failed()).isZero();
    verify(sender).send(any());
  }

  @Test
  void logsFailedWhenSenderGivesUp() {
    Subscription s = claudePro();
    when(subscriptions.findAllEligibleForReminders()).thenReturn(List.of(s));
    when(logs.findBySubscriptionIdAndRenewalDateAndOffsetDays(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(sender.send(any())).thenThrow(new NotificationFailedException("gave up", 3));

    ReminderService.ReminderResult result = service.run();

    assertThat(result.failed()).isEqualTo(1);
    assertThat(result.sent()).isZero();
    ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
    verify(logs, org.mockito.Mockito.atLeast(2)).save(captor.capture());
    List<NotificationLog> saved = captor.getAllValues();
    assertThat(saved).anyMatch(l -> NotificationLog.FAILED.equals(l.getStatus()));
  }

  @Test
  void carriesOnAfterOneSubscriptionFails() {
    Subscription s1 = aSubscription().named("Failing").renewingOn(IN_7).build();
    Subscription s2 = aSubscription().named("Succeeding").renewingOn(IN_7).build();
    when(subscriptions.findAllEligibleForReminders()).thenReturn(List.of(s1, s2));
    when(logs.findBySubscriptionIdAndRenewalDateAndOffsetDays(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(sender.send(argThat(n -> n != null && n.title().contains("Failing"))))
        .thenThrow(new RuntimeException("network failure"));
    when(sender.send(argThat(n -> n != null && n.title().contains("Succeeding")))).thenReturn(1);

    ReminderService.ReminderResult result = service.run();

    assertThat(result.sent()).isEqualTo(1);
    assertThat(result.failed()).isEqualTo(1);
  }

  @Test
  void sendsNothingWhenTopicNotConfigured() {
    ReminderService noSender =
        new ReminderService(
            subscriptions,
            logs,
            policy,
            messages,
            Optional.empty(),
            Clock.fixed(NOW, LONDON),
            TransactionOperations.withoutTransaction());

    ReminderService.ReminderResult result = noSender.run();

    assertThat(result.sent()).isZero();
    assertThat(result.failed()).isZero();
    assertThat(result.skipped()).isZero();
    verify(subscriptions, never()).findAllEligibleForReminders();
  }

  @Test
  void usesLondonDateAt0800DuringBst() {
    // 07:00 UTC on 1 Jul 2026 = 08:00 BST; London date must be 1 Jul, not 30 Jun
    Instant bstMorning = Instant.parse("2026-07-01T07:00:00Z");
    Clock bstClock = Clock.fixed(bstMorning, LONDON);
    LocalDate renewsIn7 = LocalDate.of(2026, 7, 8);
    Subscription s = aSubscription().named("BST Sub").renewingOn(renewsIn7).build();
    when(subscriptions.findAllEligibleForReminders()).thenReturn(List.of(s));
    when(logs.findBySubscriptionIdAndRenewalDateAndOffsetDays(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(sender.send(any())).thenReturn(1);

    ReminderService.ReminderResult result = service(bstClock).run();

    // If service used UTC date (30 Jun), daysLeft would be 8, no 7-day offset matches → 0 sent
    assertThat(result.sent()).isEqualTo(1);
  }
}
