package com.subsense.notification;

import static com.subsense.support.SubscriptionBuilder.TODAY;
import static com.subsense.support.SubscriptionBuilder.aSubscription;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.subsense.subscription.Decision;
import com.subsense.subscription.Subscription;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReminderPolicyTest {

  private final ReminderPolicy policy = new ReminderPolicy(List.of(14, 7, 1));

  @Test
  void sevenDayReminderIsDueWhenCancelByIsSevenDaysAway() {
    Subscription s = aSubscription().renewingOn(TODAY.plusDays(7)).build();

    assertThat(policy.due(s, TODAY))
        .hasValueSatisfying(
            r -> {
              assertThat(r.offsetDays()).isEqualTo(7);
              assertThat(r.daysLeft()).isEqualTo(7);
              assertThat(r.renewalDate()).isEqualTo(TODAY.plusDays(7));
            });
  }

  @Test
  void nothingIsDueMoreThanTheLargestOffsetAway() {
    Subscription s = aSubscription().renewingOn(TODAY.plusDays(15)).build();

    assertThat(policy.due(s, TODAY)).isEmpty();
  }

  @Test
  void catchesUpAMissedOffsetWithTheActualDaysLeft() {
    // The app was off on the 7-day morning: the 7-day reminder still goes out, saying 6 days.
    Subscription s = aSubscription().renewingOn(TODAY.plusDays(6)).build();

    assertThat(policy.due(s, TODAY))
        .hasValueSatisfying(
            r -> {
              assertThat(r.offsetDays()).isEqualTo(7);
              assertThat(r.daysLeft()).isEqualTo(6);
            });
  }

  @Test
  void countsDownToCancelByDateWhenThereIsANoticePeriod() {
    // Renews in 10 days with 3 days' notice: the last day to cancel is in 7 days.
    Subscription s = aSubscription().renewingOn(TODAY.plusDays(10)).withNoticeDays(3).build();

    assertThat(policy.due(s, TODAY)).hasValueSatisfying(r -> assertThat(r.daysLeft()).isEqualTo(7));
  }

  @Test
  void trialCountsDownToTrialEnd() {
    Subscription s = aSubscription().freeTrialThen("29.99").renewingOn(TODAY.plusDays(1)).build();

    assertThat(policy.due(s, TODAY))
        .hasValueSatisfying(r -> assertThat(r.offsetDays()).isEqualTo(1));
  }

  @Test
  void noCountdownOnceRenewDecided() {
    Subscription s = aSubscription().renewingOn(TODAY.plusDays(7)).decided(Decision.RENEW).build();

    assertThat(policy.due(s, TODAY)).isEmpty();
  }

  @Test
  void noReminderWhenDecisionIsCancel() {
    Subscription s = aSubscription().renewingOn(TODAY.plusDays(7)).decided(Decision.CANCEL).build();

    assertThat(policy.due(s, TODAY)).isEmpty();
  }

  @Test
  void renewsTodayConfirmationForPendingRenewAndDowngrade() {
    Subscription pending = aSubscription().renewingOn(TODAY).build();
    Subscription renew = aSubscription().renewingOn(TODAY).decided(Decision.RENEW).build();
    Subscription downgrade = aSubscription().renewingOn(TODAY).downgradingTo("9.00").build();
    Subscription cancel = aSubscription().renewingOn(TODAY).decided(Decision.CANCEL).build();

    for (Subscription s : List.of(pending, renew, downgrade)) {
      assertThat(policy.due(s, TODAY)).hasValueSatisfying(r -> assertThat(r.offsetDays()).isZero());
    }
    assertThat(policy.due(cancel, TODAY)).isEmpty();
  }

  @Test
  void oneDayAndTodayRemindersAreHighPriority() {
    assertThat(new DueReminder(TODAY, 0, 0).urgent()).isTrue();
    assertThat(new DueReminder(TODAY, 1, 1).urgent()).isTrue();
    assertThat(new DueReminder(TODAY, 7, 7).urgent()).isFalse();
  }

  @Test
  void rejectsOffsetsOutsideOneTo365() {
    assertThatThrownBy(() -> new ReminderPolicy(List.of(0, 7)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 1 and 365");
    assertThatThrownBy(() -> new ReminderPolicy(List.of(400)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
