package com.subsense.notification;

import com.subsense.common.SubSenseProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/settings/notifications")
@Tag(name = "Notification settings")
class NotificationSettingsController {

  private final SubSenseProperties properties;
  private final Optional<NotificationSender> sender;

  NotificationSettingsController(
      SubSenseProperties properties, Optional<NotificationSender> sender) {
    this.properties = properties;
    this.sender = sender;
  }

  @GetMapping
  @Operation(summary = "Current notification configuration (read-only)")
  NotificationSettingsView settings() {
    SubSenseProperties.Ntfy ntfy = properties.ntfy();
    String server = ntfy != null ? ntfy.server() : null;
    boolean topicConfigured = ntfy != null && ntfy.topic() != null && !ntfy.topic().isBlank();
    List<Integer> offsets =
        properties.reminders() != null && properties.reminders().offsets() != null
            ? properties.reminders().offsets()
            : List.of(14, 7, 1);
    return new NotificationSettingsView(server, topicConfigured, offsets);
  }

  @PostMapping("/test")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Send a test notification to verify the ntfy configuration")
  void sendTest() {
    if (sender.isEmpty()) {
      ProblemDetail pd =
          ProblemDetail.forStatusAndDetail(
              HttpStatus.SERVICE_UNAVAILABLE, "Notifications are not configured");
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, pd.getDetail());
    }
    Notification test =
        new Notification(
            "SubSense test notification",
            "Your ntfy integration is working.",
            false,
            properties.appUrl() != null ? properties.appUrl() : "http://localhost:3000",
            List.of("white_check_mark"));
    try {
      sender.get().send(test);
    } catch (NotificationFailedException e) {
      ProblemDetail pd =
          ProblemDetail.forStatusAndDetail(
              HttpStatus.BAD_GATEWAY, "ntfy did not accept the notification");
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, pd.getDetail());
    }
  }

  record NotificationSettingsView(String server, boolean topicConfigured, List<Integer> offsets) {}
}
