package com.subsense.subscription;

import com.subsense.common.SubSenseProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the renewal rollover daily, and on startup to catch up after the app was off. */
@Component
class RenewalRolloverJob {

  private final RenewalRolloverService rollover;
  private final SubSenseProperties properties;

  RenewalRolloverJob(RenewalRolloverService rollover, SubSenseProperties properties) {
    this.rollover = rollover;
    this.properties = properties;
  }

  @EventListener(ApplicationReadyEvent.class)
  void rollOverOnStartup() {
    if (properties.rollover().runOnStartup()) {
      rollover.run();
    }
  }

  @Scheduled(cron = "${subsense.rollover.cron}", zone = "${subsense.time-zone}")
  void rollOverDaily() {
    rollover.run();
  }
}
