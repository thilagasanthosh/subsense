package com.subsense.currency;

import static com.subsense.currency.CurrencyCode.GBP;
import static com.subsense.currency.CurrencyCode.INR;
import static com.subsense.currency.CurrencyCode.USD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FxRateServiceTest {

  private static final LocalDate RATE_DATE = LocalDate.of(2026, 10, 30);

  private final FxRateRepository repository = mock(FxRateRepository.class);
  private final FxRateProvider provider = mock(FxRateProvider.class);
  private final FxRateService service = new FxRateService(repository, provider);

  @Test
  void storesOneRowPerCurrencyForANewDay() {
    when(provider.fetchLatest()).thenReturn(latestRates());
    when(repository.existsByRateDate(RATE_DATE)).thenReturn(false);

    assertThat(service.refresh()).isTrue();

    verify(repository, times(3)).save(any(FxRate.class));
  }

  @Test
  void doesNotStoreTheSameDayTwice() {
    when(provider.fetchLatest()).thenReturn(latestRates());
    when(repository.existsByRateDate(RATE_DATE)).thenReturn(true);

    assertThat(service.refresh()).isTrue();

    verify(repository, never()).save(any(FxRate.class));
  }

  @Test
  void keepsCachedRatesWhenTheServiceIsDown() {
    when(provider.fetchLatest()).thenThrow(new IllegalStateException("503"));

    assertThat(service.refresh()).isFalse();

    verify(repository, never()).save(any(FxRate.class));
  }

  private static ExchangeRates latestRates() {
    return new ExchangeRates(
        RATE_DATE,
        Map.of(
            GBP, new BigDecimal("0.85"),
            INR, new BigDecimal("90"),
            USD, new BigDecimal("1.10")));
  }
}
