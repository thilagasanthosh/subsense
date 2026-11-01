package com.subsense.subscription;

import static com.subsense.support.SubscriptionBuilder.NOW;
import static com.subsense.support.SubscriptionBuilder.aSubscription;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.subsense.common.FieldValidationException;
import com.subsense.common.NotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SubscriptionServiceTest {

  private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
  private final CategoryRepository categories = mock(CategoryRepository.class);
  private final DecisionHistoryRepository history = mock(DecisionHistoryRepository.class);
  private final SubscriptionService service =
      new SubscriptionService(
          subscriptions, categories, history, Clock.fixed(NOW, ZoneId.of("Europe/London")));

  @BeforeEach
  void knownCategory() {
    when(categories.findById(1L)).thenReturn(Optional.of(new Category("AI Tools", "#6366F1")));
    when(subscriptions.save(any(Subscription.class))).thenAnswer(call -> call.getArgument(0));
  }

  @Test
  void createsAValidSubscription() {
    Subscription created = service.create(aSubscription().named("Netflix").details());

    assertThat(created.getName()).isEqualTo("Netflix");
    verify(subscriptions).save(created);
  }

  @Test
  void rejectsARenewalDateBeforeTheStartDate() {
    var details =
        aSubscription()
            .startingOn(LocalDate.of(2026, 11, 10))
            .renewingOn(LocalDate.of(2026, 11, 1))
            .details();

    assertThatThrownBy(() -> service.create(details))
        .isInstanceOf(FieldValidationException.class)
        .extracting(e -> ((FieldValidationException) e).errors())
        .isEqualTo(Map.of("nextRenewalDate", "must be on or after the start date"));
    verify(subscriptions, never()).save(any());
  }

  @Test
  void rejectsAFreeTrialWithoutAPriceAfterTheTrial() {
    var details = aSubscription().freeTrialThen(null).details();

    assertThatThrownBy(() -> service.create(details))
        .isInstanceOf(FieldValidationException.class)
        .hasMessage("is required for a free trial");
  }

  @Test
  void rejectsAnUnknownCategory() {
    var details = aSubscription().withCategoryId(99L).details();

    assertThatThrownBy(() -> service.create(details))
        .isInstanceOf(FieldValidationException.class)
        .hasMessage("is not a known category");
  }

  @Test
  void recordsEveryDecisionInTheAuditTrail() {
    Subscription s = aSubscription().build();
    when(subscriptions.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(s));

    service.decide(
        7L, new DecisionRequest(Decision.CANCEL, null, "Not using it"), DecisionSource.UI);

    ArgumentCaptor<DecisionHistory> saved = ArgumentCaptor.forClass(DecisionHistory.class);
    verify(history).save(saved.capture());
    assertThat(saved.getValue().getDecision()).isEqualTo(Decision.CANCEL);
    assertThat(saved.getValue().getSource()).isEqualTo(DecisionSource.UI);
    assertThat(saved.getValue().getReason()).isEqualTo("Not using it");
    assertThat(s.status()).isEqualTo(SubscriptionStatus.CANCELLING);
  }

  @Test
  void requiresANewAmountToDowngrade() {
    assertThatThrownBy(
            () ->
                service.decide(
                    7L, new DecisionRequest(Decision.DOWNGRADE, null, null), DecisionSource.UI))
        .isInstanceOf(FieldValidationException.class)
        .hasMessage("is required when downgrading");
  }

  @Test
  void reportsAMissingSubscription() {
    when(subscriptions.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(42L)).isInstanceOf(NotFoundException.class);
  }
}
