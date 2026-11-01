package com.subsense.currency;

import com.subsense.common.SubSenseProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class FxRateRefreshJob {

  private final FxRateService fxRateService;
  private final SubSenseProperties properties;

  FxRateRefreshJob(FxRateService fxRateService, SubSenseProperties properties) {
    this.fxRateService = fxRateService;
    this.properties = properties;
  }

  @EventListener(ApplicationReadyEvent.class)
  void refreshOnStartup() {
    if (properties.fx().refreshOnStartup()) {
      fxRateService.refresh();
    }
  }

  @Scheduled(cron = "${subsense.fx.refresh-cron}", zone = "${subsense.time-zone}")
  void refreshDaily() {
    fxRateService.refresh();
  }
}
