package com.subsense.currency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "fx_rate")
class FxRate {

  static final String EURO = "EUR";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String base;

  @Column(nullable = false)
  private String quote;

  @Column(nullable = false)
  private BigDecimal rate;

  @Column(name = "rate_date", nullable = false)
  private LocalDate rateDate;

  protected FxRate() {}

  FxRate(CurrencyCode quote, BigDecimal rate, LocalDate rateDate) {
    this.base = EURO;
    this.quote = quote.name();
    this.rate = rate;
    this.rateDate = rateDate;
  }

  String quote() {
    return quote;
  }

  BigDecimal rate() {
    return rate;
  }

  LocalDate rateDate() {
    return rateDate;
  }
}
