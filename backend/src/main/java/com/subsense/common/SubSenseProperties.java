package com.subsense.common;

import com.subsense.currency.CurrencyCode;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "subsense")
public record SubSenseProperties(
    CurrencyCode reportingCurrency,
    ZoneId timeZone,
    Fx fx,
    Rollover rollover,
    Ntfy ntfy,
    Reminders reminders,
    String appUrl) {

  public record Fx(String baseUrl, String refreshCron, boolean refreshOnStartup) {}

  public record Rollover(String cron, boolean runOnStartup) {}

  /** ntfy push-notification settings. {@code topic} empty/null means reminders are disabled. */
  public record Ntfy(String server, String topic, String token, Duration retryBackoff) {}

  public record Reminders(List<Integer> offsets, String cron, boolean runOnStartup) {}
}
