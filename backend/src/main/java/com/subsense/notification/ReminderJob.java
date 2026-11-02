package com.subsense.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Fires the reminder job on a cron schedule and once on startup (configurable). */
@Component
class ReminderJob {

  private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);

  private final ReminderService reminderService;
  private final boolean runOnStartup;

  ReminderJob(ReminderService reminderService, com.subsense.common.SubSenseProperties properties) {
    this.reminderService = reminderService;
    this.runOnStartup = properties.reminders() != null && properties.reminders().runOnStartup();
  }

  @Scheduled(cron = "${subsense.reminders.cron:0 0 8 * * *}", zone = "Europe/London")
  void runScheduled() {
    log.info("Scheduled reminder job starting");
    reminderService.run();
  }

  @EventListener(ApplicationReadyEvent.class)
  void runOnStartup() {
    if (runOnStartup) {
      log.info("Startup reminder job starting");
      reminderService.run();
    }
  }
}
