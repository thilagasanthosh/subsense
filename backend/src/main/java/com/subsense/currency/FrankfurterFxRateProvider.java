package com.subsense.currency;

import com.subsense.common.SubSenseProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Free European Central Bank reference rates from the Frankfurter API (no key needed). Timeouts
 * come from spring.http.client.* in application.yml.
 */
@Component
class FrankfurterFxRateProvider implements FxRateProvider {

  private final RestClient restClient;

  FrankfurterFxRateProvider(RestClient.Builder builder, SubSenseProperties properties) {
    this.restClient = builder.baseUrl(properties.fx().baseUrl()).build();
  }

  @Override
  public ExchangeRates fetchLatest() {
    String symbols =
        Arrays.stream(CurrencyCode.values()).map(Enum::name).collect(Collectors.joining(","));
    LatestResponse response =
        restClient
            .get()
            .uri("/latest?base=EUR&symbols=" + symbols)
            .retrieve()
            .body(LatestResponse.class);
    if (response == null || response.rates() == null) {
      throw new IllegalStateException("Empty response from exchange rate service");
    }
    Map<CurrencyCode, BigDecimal> perEuro = new EnumMap<>(CurrencyCode.class);
    response.rates().forEach((code, rate) -> perEuro.put(CurrencyCode.valueOf(code), rate));
    return new ExchangeRates(response.date(), perEuro);
  }

  record LatestResponse(String base, LocalDate date, Map<String, BigDecimal> rates) {}
}
