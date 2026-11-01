package com.subsense.support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/** A clock tests can move forward, to run daily jobs on chosen dates. */
public final class MutableClock extends Clock {

  private final ZoneId zone;
  private Instant now;

  public MutableClock(Instant now, ZoneId zone) {
    this.now = now;
    this.zone = zone;
  }

  /** Moves to 00:15 on the given day in this clock's zone, when the daily rollover runs. */
  public void setDate(LocalDate date) {
    this.now = date.atTime(LocalTime.of(0, 15)).atZone(zone).toInstant();
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return new MutableClock(now, zone);
  }

  @Override
  public Instant instant() {
    return now;
  }
}
