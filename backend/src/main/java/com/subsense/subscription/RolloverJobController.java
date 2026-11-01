package com.subsense.subscription;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only trigger for end-to-end scenarios; absent unless subsense.jobs.manual-trigger=true. */
@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "Jobs (test only)")
@ConditionalOnProperty(name = "subsense.jobs.manual-trigger", havingValue = "true")
class RolloverJobController {

  private final RenewalRolloverService rollover;

  RolloverJobController(RenewalRolloverService rollover) {
    this.rollover = rollover;
  }

  @PostMapping("/rollover")
  @Operation(summary = "Run the daily renewal rollover now (end-to-end tests only)")
  RolloverResult rollOver() {
    return new RolloverResult(rollover.run());
  }

  record RolloverResult(int processed) {}
}
