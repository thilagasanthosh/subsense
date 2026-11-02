package com.subsense.notification;

import com.subsense.common.SubSenseProperties;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Sends push notifications via ntfy (https://ntfy.sh) using JSON publish at the server root.
 * Headers are avoided because HTTP headers cannot carry non-Latin-1 characters (e.g. ₹ in a title);
 * posting the payload as JSON has no such restriction.
 *
 * <p>Retries up to 3 times on 429 / 5xx / network errors with exponential back-off. Gives up
 * immediately on any other 4xx.
 */
class NtfyNotificationSender implements NotificationSender {

  private static final Logger log = LoggerFactory.getLogger(NtfyNotificationSender.class);
  private static final int MAX_ATTEMPTS = 3;
  private static final long MAX_RETRY_AFTER_SECONDS = 10L;

  private final RestClient client;
  private final String topic;
  private final String token;
  private final Duration retryBackoff;
  private final Consumer<Duration> sleeper;

  NtfyNotificationSender(
      RestClient.Builder builder, SubSenseProperties.Ntfy ntfy, Consumer<Duration> sleeper) {
    this.client = builder.baseUrl(ntfy.server()).build();
    this.topic = ntfy.topic();
    this.token = ntfy.token();
    this.retryBackoff = ntfy.retryBackoff();
    this.sleeper = sleeper;
  }

  @Override
  public int send(Notification notification) {
    RuntimeException lastError = null;
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        doSend(notification);
        log.info("Reminder sent after {} attempt(s)", attempt);
        return attempt;
      } catch (HttpClientErrorException e) {
        if (e.getStatusCode().value() == 429) {
          lastError = e;
          if (attempt < MAX_ATTEMPTS) {
            sleeper.accept(retryAfterDelay(e));
          }
        } else {
          log.warn("ntfy rejected notification: {} — not retrying", e.getStatusCode());
          throw new NotificationFailedException("ntfy rejected: " + e.getStatusCode(), attempt);
        }
      } catch (HttpServerErrorException | ResourceAccessException e) {
        lastError = e;
        if (attempt < MAX_ATTEMPTS) {
          sleeper.accept(retryBackoff.multipliedBy(attempt));
        }
      }
    }
    log.warn("Giving up on reminder after {} attempts: {}", MAX_ATTEMPTS, lastError.getMessage());
    throw new NotificationFailedException(
        "ntfy gave up after " + MAX_ATTEMPTS + " attempts", MAX_ATTEMPTS);
  }

  private void doSend(Notification notification) {
    RestClient.RequestBodySpec req = client.post().uri("/");
    if (token != null && !token.isBlank()) {
      req = req.header("Authorization", "Bearer " + token);
    }
    req.contentType(MediaType.APPLICATION_JSON)
        .body(
            new NtfyPayload(
                topic,
                notification.title(),
                notification.message(),
                notification.urgent() ? 4 : 3,
                notification.clickUrl(),
                notification.tags()))
        .retrieve()
        .toBodilessEntity();
  }

  private Duration retryAfterDelay(HttpClientErrorException e) {
    String header =
        e.getResponseHeaders() != null ? e.getResponseHeaders().getFirst("Retry-After") : null;
    if (header != null) {
      try {
        long seconds = Long.parseLong(header.trim());
        return Duration.ofSeconds(Math.min(seconds, MAX_RETRY_AFTER_SECONDS));
      } catch (NumberFormatException ignored) {
        // fall through to default
      }
    }
    return retryBackoff;
  }

  record NtfyPayload(
      String topic, String title, String message, int priority, String click, List<String> tags) {}
}
