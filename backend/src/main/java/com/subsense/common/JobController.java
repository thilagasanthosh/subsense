package com.subsense.common;

import com.subsense.notification.ReminderService;
import com.subsense.notification.ReminderService.ReminderResult;
import com.subsense.subscription.RenewalRolloverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only triggers for end-to-end scenarios; absent unless subsense.jobs.manual-trigger=true. */
@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "Jobs (test only)")
@ConditionalOnProperty(name = "subsense.jobs.manual-trigger", havingValue = "true")
public class JobController {

  private final RenewalRolloverService rollover;
  private final ReminderService reminders;

  JobController(RenewalRolloverService rollover, ReminderService reminders) {
    this.rollover = rollover;
    this.reminders = reminders;
  }

  @PostMapping("/rollover")
  @Operation(summary = "Run the daily renewal rollover now (end-to-end tests only)")
  RolloverResult rollOver() {
    return new RolloverResult(rollover.run());
  }

  @PostMapping("/reminders")
  @Operation(summary = "Run the reminder job now (end-to-end tests only)")
  ReminderResult remind() {
    return reminders.run();
  }

  record RolloverResult(int processed) {}
}
