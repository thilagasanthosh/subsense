package com.subsense.currency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps a daily cache of exchange rates. If the rate service is down, the last cached rates are
 * used, and callers show their date.
 */
@Service
public class FxRateService {

  private static final Logger log = LoggerFactory.getLogger(FxRateService.class);

  private final FxRateRepository repository;
  private final FxRateProvider provider;

  FxRateService(FxRateRepository repository, FxRateProvider provider) {
    this.repository = repository;
    this.provider = provider;
  }

  /** Fetches and stores the latest rates. Returns false if the service could not be reached. */
  @Transactional
  public boolean refresh() {
    ExchangeRates latest;
    try {
      latest = provider.fetchLatest();
    } catch (RuntimeException ex) {
      log.warn("Exchange rate refresh failed; keeping cached rates: {}", ex.getMessage());
      return false;
    }
    if (repository.existsByRateDate(latest.asOf())) {
      log.info("Exchange rates for {} already cached", latest.asOf());
      return true;
    }
    latest
        .perEuro()
        .forEach((currency, rate) -> repository.save(new FxRate(currency, rate, latest.asOf())));
    log.info("Cached exchange rates for {}", latest.asOf());
    return true;
  }

  @Transactional(readOnly = true)
  public ExchangeRates current() {
    LocalDate latestDate =
        repository
            .findLatestRateDate()
            .orElseThrow(() -> new IllegalStateException("No exchange rates cached"));
    Map<CurrencyCode, BigDecimal> perEuro = new EnumMap<>(CurrencyCode.class);
    for (FxRate rate : repository.findByRateDate(latestDate)) {
      perEuro.put(CurrencyCode.valueOf(rate.quote()), rate.rate());
    }
    return new ExchangeRates(latestDate, perEuro);
  }
}
