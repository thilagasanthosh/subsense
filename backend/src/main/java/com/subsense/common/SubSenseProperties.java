package com.subsense.common;

import com.subsense.currency.CurrencyCode;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "subsense")
public record SubSenseProperties(CurrencyCode reportingCurrency, ZoneId timeZone, Fx fx) {

  public record Fx(String baseUrl, String refreshCron, boolean refreshOnStartup) {}
}
