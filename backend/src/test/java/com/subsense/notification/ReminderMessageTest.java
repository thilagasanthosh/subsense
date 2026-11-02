package com.subsense.notification;

import static com.subsense.currency.CurrencyCode.GBP;
import static com.subsense.currency.CurrencyCode.INR;
import static com.subsense.support.SubscriptionBuilder.TODAY;
import static com.subsense.support.SubscriptionBuilder.aSubscription;
import static org.assertj.core.api.Assertions.assertThat;

import com.subsense.subscription.Subscription;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReminderMessageTest {

  private static final LocalDate IN_7_DAYS = TODAY.plusDays(7); // 8 Nov 2026

  private final ReminderMessage messages = new ReminderMessage("https://subsense.example");

  @Test
  void titleSaysRenewsInDays() {
    Subscription s = aSubscription().named("Claude Pro").renewingOn(IN_7_DAYS).build();

    assertThat(messages.notificationFor(s, new DueReminder(IN_7_DAYS, 7, 7)).title())
        .isEqualTo("Claude Pro renews in 7 days");
  }

  @Test
  void bodyShowsNativeAmountCycleCancelByAndDecision() {
    Subscription s = aSubscription().monthly().costing("349.00", INR).renewingOn(IN_7_DAYS).build();

    assertThat(messages.notificationFor(s, new DueReminder(IN_7_DAYS, 7, 7)).message())
        .isEqualTo("₹349.00 monthly · cancel by 8 Nov 2026 · not decided yet");
  }

  @Test
  void titleSaysTomorrowForOneDay() {
    Subscription s = aSubscription().named("Netflix").renewingOn(TODAY.plusDays(1)).build();

    assertThat(messages.notificationFor(s, new DueReminder(TODAY.plusDays(1), 1, 1)).title())
        .isEqualTo("Netflix renews tomorrow");
  }

  @Test
  void titleSaysDaysLeftToCancelWithNotice() {
    Subscription s =
        aSubscription().named("Gym").renewingOn(TODAY.plusDays(10)).withNoticeDays(7).build();

    assertThat(messages.notificationFor(s, new DueReminder(TODAY.plusDays(10), 7, 3)).title())
        .isEqualTo("Gym: 3 days left to cancel");
  }

  @Test
  void titleSaysTrialEnds() {
    Subscription s =
        aSubscription()
            .named("LinkedIn Premium")
            .freeTrialThen("29.99")
            .renewingOn(IN_7_DAYS)
            .build();

    Notification n = messages.notificationFor(s, new DueReminder(IN_7_DAYS, 7, 7));

    assertThat(n.title()).isEqualTo("LinkedIn Premium trial ends in 7 days");
    assertThat(n.message()).startsWith("£29.99 monthly after the trial");
  }

  @Test
  void titleSaysRenewsTodayAndBodyRepeatsTheDecision() {
    Subscription s =
        aSubscription().named("Claude Pro").renewingOn(TODAY).downgradingTo("9.00").build();

    Notification n = messages.notificationFor(s, new DueReminder(TODAY, 0, 0));

    assertThat(n.title()).isEqualTo("Claude Pro renews today");
    assertThat(n.message()).endsWith("you chose to downgrade to £9.00");
    assertThat(n.urgent()).isTrue();
  }

  @Test
  void bodyNeverContainsPaymentLabelOrNotes() {
    Subscription s =
        aSubscription()
            .withPaymentLabel("Visa 4417")
            .costing("18.00", GBP)
            .renewingOn(IN_7_DAYS)
            .build();

    assertThat(messages.notificationFor(s, new DueReminder(IN_7_DAYS, 7, 7)).message())
        .doesNotContain("Visa")
        .doesNotContain("4417");
  }

  @Test
  void clickOpensTheSubscription() {
    Subscription s = aSubscription().renewingOn(IN_7_DAYS).build();
    ReflectionTestUtils.setField(s, "id", 42L);

    assertThat(messages.notificationFor(s, new DueReminder(IN_7_DAYS, 7, 7)).clickUrl())
        .isEqualTo("https://subsense.example/subscriptions/42");
  }
}
