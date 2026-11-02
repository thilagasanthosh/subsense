package com.subsense.notification;

import com.subsense.common.SubSenseProperties;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
class NotificationConfiguration {

  @Bean
  ReminderPolicy reminderPolicy(SubSenseProperties properties) {
    List<Integer> offsets =
        properties.reminders() != null && properties.reminders().offsets() != null
            ? properties.reminders().offsets()
            : List.of(14, 7, 1);
    return new ReminderPolicy(offsets);
  }

  @Bean
  ReminderMessage reminderMessage(SubSenseProperties properties) {
    String appUrl = properties.appUrl() != null ? properties.appUrl() : "http://localhost:3000";
    return new ReminderMessage(appUrl);
  }

  /**
   * Registered only when a topic is configured. Spring's native {@code Optional<T>} injection gives
   * callers {@code Optional.empty()} when the bean is absent (topic not set).
   */
  @Bean
  @ConditionalOnProperty(name = "subsense.ntfy.topic")
  NotificationSender notificationSender(RestClient.Builder builder, SubSenseProperties properties) {
    Consumer<Duration> sleeper =
        d -> {
          try {
            Thread.sleep(d);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        };
    return new NtfyNotificationSender(builder, properties.ntfy(), sleeper);
  }
}
