package com.subsense.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.subsense.common.SubSenseProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class NtfyNotificationSenderTest {

  private static final String SERVER = "https://ntfy.example";
  private static final Notification CLAUDE =
      new Notification(
          "Claude Pro renews in 7 days",
          "£18.00 monthly · cancel by 8 Nov 2026 · not decided yet",
          false,
          "https://subsense.example/subscriptions/42",
          List.of("bell"));

  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final List<Duration> sleeps = new ArrayList<>();

  private NtfyNotificationSender sender(String token) {
    return new NtfyNotificationSender(
        builder,
        new SubSenseProperties.Ntfy(SERVER, "my-topic", token, Duration.ofSeconds(1)),
        sleeps::add);
  }

  @Test
  void postsMessageWithTitlePriorityClickAndTags() {
    server
        .expect(requestTo(SERVER + "/"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(
            content()
                .json(
                    """
                    {"topic":"my-topic","title":"Claude Pro renews in 7 days",
                     "message":"£18.00 monthly · cancel by 8 Nov 2026 · not decided yet",
                     "priority":3,"click":"https://subsense.example/subscriptions/42",
                     "tags":["bell"]}
                    """,
                    true))
        .andRespond(withSuccess("{\"id\":\"x\"}", MediaType.APPLICATION_JSON));

    assertThat(sender(null).send(CLAUDE)).isEqualTo(1);
    server.verify();
  }

  @Test
  void sendsBearerTokenWhenConfigured() {
    server
        .expect(requestTo(SERVER + "/"))
        .andExpect(header("Authorization", "Bearer secret-token"))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    assertThat(sender("secret-token").send(CLAUDE)).isEqualTo(1);
    server.verify();
  }

  @Test
  void retries503ThenSucceeds() {
    server.expect(requestTo(SERVER + "/")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    server
        .expect(requestTo(SERVER + "/"))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    assertThat(sender(null).send(CLAUDE)).isEqualTo(2);
    assertThat(sleeps).containsExactly(Duration.ofSeconds(1)); // retryBackoff * 1
    server.verify();
  }

  @Test
  void retries429HonouringRetryAfter() {
    server
        .expect(requestTo(SERVER + "/"))
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "5"));
    server
        .expect(requestTo(SERVER + "/"))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    assertThat(sender(null).send(CLAUDE)).isEqualTo(2);
    assertThat(sleeps).containsExactly(Duration.ofSeconds(5));
    server.verify();
  }

  @Test
  void givesUpAfterThreeAttempts() {
    for (int i = 0; i < 3; i++) {
      server.expect(requestTo(SERVER + "/")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    }

    assertThatThrownBy(() -> sender(null).send(CLAUDE))
        .isInstanceOf(NotificationFailedException.class)
        .extracting(e -> ((NotificationFailedException) e).getAttempts())
        .isEqualTo(3);
    assertThat(sleeps).hasSize(2); // backoff after attempt 1 and 2, not 3
    server.verify();
  }

  @Test
  void doesNotRetryOther4xx() {
    server.expect(requestTo(SERVER + "/")).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

    assertThatThrownBy(() -> sender(null).send(CLAUDE))
        .isInstanceOf(NotificationFailedException.class)
        .extracting(e -> ((NotificationFailedException) e).getAttempts())
        .isEqualTo(1);
    assertThat(sleeps).isEmpty();
    server.verify();
  }
}
