package com.subsense.currency;

/** A source of the latest exchange rates per euro. */
public interface FxRateProvider {

  ExchangeRates fetchLatest();
}
