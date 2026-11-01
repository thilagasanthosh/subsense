package com.subsense.subscription;

import static com.subsense.support.SubscriptionBuilder.NOW;
import static com.subsense.support.SubscriptionBuilder.TODAY;
import static com.subsense.support.SubscriptionBuilder.aSubscription;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;

class RenewalRolloverServiceTest {

  private static final ZoneId LONDON = ZoneId.of("Europe/London");

  private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
  private final DecisionHistoryRepository history = mock(DecisionHistoryRepository.class);

  private RenewalRolloverService serviceAt(Clock clock) {
    return new RenewalRolloverService(
        subscriptions, history, TransactionOperations.withoutTransaction(), clock);
  }

  private final RenewalRolloverService service = serviceAt(Clock.fixed(NOW, LONDON));

  private void stored(long id, Subscription subscription) {
    when(subscriptions.findIdsDueForRollover(any())).thenReturn(List.of(id));
    when(subscriptions.findById(id)).thenReturn(Optional.of(subscription));
  }

  @Test
  void leavesRenewalDueTodayUntouched() {
    Subscription s = aSubscription().renewingOn(TODAY).decided(Decision.RENEW).build();
    stored(1L, s);

    assertThat(service.run()).isZero();

    assertThat(s.getNextRenewalDate()).isEqualTo(TODAY);
    assertThat(s.getDecision()).isEqualTo(Decision.RENEW);
    verify(history, never()).save(any());
  }

  @Test
  void advancesRenewalThatPassedYesterday() {
    Subscription s = aSubscription().monthly().renewingOn(TODAY.minusDays(1)).build();
    stored(1L, s);

    assertThat(service.run()).isEqualTo(1);

    assertThat(s.getNextRenewalDate()).isEqualTo(LocalDate.of(2026, 11, 30));
  }

  @Test
  void catchesUpSeveralMissedMonthlyRenewals() {
    Subscription s =
        aSubscription()
            .monthly()
            .startingOn(LocalDate.of(2026, 1, 1))
            .renewingOn(LocalDate.of(2026, 8, 31))
            .decided(Decision.RENEW)
            .build();
    stored(1L, s);

    service.run();

    // 31 Aug -> 30 Sep -> 31 Oct -> 30 Nov (the first date on or after 1 Nov)
    assertThat(s.getNextRenewalDate()).isEqualTo(LocalDate.of(2026, 11, 30));
    assertThat(s.getDecision()).isEqualTo(Decision.PENDING);
  }

  @Test
  void recordsSystemHistoryEntryForEachChange() {
    stored(1L, aSubscription().monthly().renewingOn(LocalDate.of(2026, 10, 31)).build());

    service.run();

    ArgumentCaptor<DecisionHistory> entry = ArgumentCaptor.forClass(DecisionHistory.class);
    verify(history).save(entry.capture());
    assertThat(entry.getValue().getSource()).isEqualTo(DecisionSource.SYSTEM);
    assertThat(entry.getValue().getDecision()).isEqualTo(Decision.PENDING);
    assertThat(entry.getValue().getReason())
        .isEqualTo("Renewed on 2026-10-31; next renewal 2026-11-30");
  }

  @Test
  void recordsWhyASubscriptionWasCancelled() {
    stored(1L, aSubscription().renewingOn(TODAY).decided(Decision.CANCEL).build());

    service.run();

    ArgumentCaptor<DecisionHistory> entry = ArgumentCaptor.forClass(DecisionHistory.class);
    verify(history).save(entry.capture());
    assertThat(entry.getValue().getDecision()).isEqualTo(Decision.CANCEL);
    assertThat(entry.getValue().getReason()).isEqualTo("Cancelled on 2026-11-01");
  }

  @Test
  void secondRunOnSameDayChangesNothing() {
    stored(1L, aSubscription().monthly().renewingOn(LocalDate.of(2026, 10, 31)).build());
    service.run();

    assertThat(service.run()).isZero();

    verify(history, times(1)).save(any());
  }

  @Test
  void ignoresSubscriptionsThatAreAlreadyCancelled() {
    Subscription s = aSubscription().renewingOn(TODAY).decided(Decision.CANCEL).build();
    s.rollOver(TODAY, NOW);
    stored(1L, s);

    assertThat(service.run()).isZero();
    verify(history, never()).save(any());
  }

  @Test
  void usesLondonDateNotUtcJustAfterMidnight() {
    // 23:30 UTC on 31 Mar is 00:30 BST on 1 Apr, so a 31 Mar renewal has passed in London.
    Clock justAfterMidnightInLondon = Clock.fixed(Instant.parse("2027-03-31T23:30:00Z"), LONDON);
    Subscription s =
        aSubscription()
            .monthly()
            .startingOn(LocalDate.of(2027, 1, 1))
            .renewingOn(LocalDate.of(2027, 3, 31))
            .build();
    stored(1L, s);

    serviceAt(justAfterMidnightInLondon).run();

    assertThat(s.getNextRenewalDate()).isEqualTo(LocalDate.of(2027, 4, 30));
  }

  @Test
  void carriesOnWhenOneSubscriptionFailsToSave() {
    Subscription healthy = aSubscription().monthly().renewingOn(TODAY.minusDays(1)).build();
    when(subscriptions.findIdsDueForRollover(any())).thenReturn(List.of(1L, 2L));
    when(subscriptions.findById(1L)).thenThrow(new IllegalStateException("row locked"));
    when(subscriptions.findById(2L)).thenReturn(Optional.of(healthy));

    assertThat(service.run()).isEqualTo(1);

    assertThat(healthy.getNextRenewalDate()).isEqualTo(LocalDate.of(2026, 11, 30));
  }
}
