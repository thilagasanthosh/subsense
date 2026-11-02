package com.subsense.currency;

import static com.subsense.currency.CurrencyCode.GBP;
import static com.subsense.currency.CurrencyCode.INR;
import static com.subsense.currency.CurrencyCode.USD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.subsense.common.SubSenseProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class FrankfurterFxRateProviderTest {

  private static final String BASE_URL = "https://fx.example";

  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final FrankfurterFxRateProvider provider =
      new FrankfurterFxRateProvider(
          builder,
          new SubSenseProperties(
              GBP,
              ZoneId.of("Europe/London"),
              new SubSenseProperties.Fx(BASE_URL, "0 0 6 * * *", false),
              null,
              null,
              null,
              null));

  @Test
  void readsRatesPerEuroForEverySupportedCurrency() {
    server
        .expect(requestTo(BASE_URL + "/latest?base=EUR&symbols=GBP,INR,USD"))
        .andRespond(
            withSuccess(
                """
                {"amount":1.0,"base":"EUR","date":"2026-10-30",
                 "rates":{"GBP":0.8531,"INR":91.24,"USD":1.0912}}
                """,
                MediaType.APPLICATION_JSON));

    ExchangeRates rates = provider.fetchLatest();

    assertThat(rates.asOf()).isEqualTo(LocalDate.of(2026, 10, 30));
    assertThat(rates.perEuro())
        .containsEntry(GBP, new BigDecimal("0.8531"))
        .containsEntry(INR, new BigDecimal("91.24"))
        .containsEntry(USD, new BigDecimal("1.0912"));
  }

  @Test
  void failsWhenTheServiceErrors() {
    server
        .expect(requestTo(BASE_URL + "/latest?base=EUR&symbols=GBP,INR,USD"))
        .andRespond(withServerError());

    assertThatThrownBy(provider::fetchLatest).isInstanceOf(RestClientException.class);
  }
}
