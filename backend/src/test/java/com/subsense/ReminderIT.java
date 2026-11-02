package com.subsense;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.moreThan;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.jayway.jsonpath.JsonPath;
import com.subsense.support.MutableClock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** F-05 integration tests: reminders against a real PostgreSQL and a WireMock ntfy stub. */
@SpringBootTest(
    properties = {
      "subsense.fx.refresh-on-startup=false",
      "subsense.rollover.run-on-startup=false",
      "subsense.reminders.run-on-startup=false",
      "subsense.jobs.manual-trigger=true",
      "subsense.ntfy.topic=subsense-it",
      "subsense.ntfy.retry-backoff=10ms",
      // WireMock uses plain HTTP/1.1 (Jetty without HTTP/2). The JDK HttpClient default on Java 21
      // attempts h2c upgrade and gets RST_STREAM. 'simple' forces HttpURLConnection (HTTP/1.1).
      "spring.http.client.factory=simple"
    })
@AutoConfigureMockMvc
@Testcontainers
class ReminderIT {

  private static final ZoneId LONDON = ZoneId.of("Europe/London");
  private static WireMockServer ntfy;

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @BeforeAll
  static void startNtfy() {
    // @DynamicPropertySource may have already started WireMock; don't overwrite it
    if (ntfy == null) {
      ntfy = new WireMockServer(WireMockConfiguration.options().dynamicPort());
      ntfy.start();
      ntfy.stubFor(
          WireMock.post(urlEqualTo("/")).willReturn(aResponse().withStatus(200).withBody("{}")));
    }
  }

  @AfterAll
  static void stopNtfy() {
    ntfy.stop();
  }

  @DynamicPropertySource
  static void ntfyProperties(DynamicPropertyRegistry registry) {
    // ntfy must be started before this is called; WireMockServer is a static field
    // so we start it here if not yet started (DynamicPropertySource runs before @BeforeAll)
    if (ntfy == null) {
      ntfy = new WireMockServer(WireMockConfiguration.options().dynamicPort());
      ntfy.start();
      ntfy.stubFor(
          WireMock.post(urlEqualTo("/")).willReturn(aResponse().withStatus(200).withBody("{}")));
    }
    registry.add("subsense.ntfy.server", () -> "http://localhost:" + ntfy.port());
  }

  @TestConfiguration
  static class TestConfig {
    @Bean
    @Primary
    MutableClock movableClock() {
      return new MutableClock(Instant.parse("2026-11-01T09:00:00Z"), LONDON);
    }
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired MutableClock clock;

  @BeforeEach
  void clean() {
    clock.setDate(LocalDate.of(2026, 11, 1));
    ntfy.resetAll();
    ntfy.stubFor(
        WireMock.post(urlEqualTo("/")).willReturn(aResponse().withStatus(200).withBody("{}")));
    jdbc.update("DELETE FROM notification_log");
    jdbc.update("DELETE FROM decision_history");
    jdbc.update("DELETE FROM subscription");
  }

  // ── AC1: reminder is sent when due ──────────────────────────────────────────

  @Test
  void sendsReminderWhenDueAndLogsItAsSent() throws Exception {
    long id = create("Claude Pro", "2026-11-08"); // 7 days away

    runReminders().andExpect(jsonPath("$.sent", is(1)));

    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM notification_log WHERE subscription_id=?", String.class, id))
        .isEqualTo("SENT");
    ntfy.verify(1, postRequestedFor(urlEqualTo("/")));
  }

  // ── AC2: running the job twice sends only one notification ──────────────────

  @Test
  void runningJobTwiceOnSameDaySendsOnlyOneNotification() throws Exception {
    create("Netflix", "2026-11-08");

    runReminders().andExpect(jsonPath("$.sent", is(1)));
    runReminders().andExpect(jsonPath("$.sent", is(0)));

    ntfy.verify(1, postRequestedFor(urlEqualTo("/")));
  }

  // ── AC3: failure on first run is retried next run ───────────────────────────

  @Test
  void resendsOnNextRunAfterFailure() throws Exception {
    // First run: ntfy returns 503 three times → FAILED
    ntfy.resetAll();
    ntfy.stubFor(WireMock.post(urlEqualTo("/")).willReturn(aResponse().withStatus(503)));

    long id = create("Spotify", "2026-11-08");
    runReminders().andExpect(jsonPath("$.failed", is(1)));
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM notification_log WHERE subscription_id=?", String.class, id))
        .isEqualTo("FAILED");

    // Second run next day: ntfy is healthy again
    ntfy.resetAll();
    ntfy.stubFor(
        WireMock.post(urlEqualTo("/")).willReturn(aResponse().withStatus(200).withBody("{}")));
    clock.setDate(LocalDate.of(2026, 11, 2));

    runReminders().andExpect(jsonPath("$.sent", is(1)));
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM notification_log WHERE subscription_id=?", String.class, id))
        .isEqualTo("SENT");
  }

  // ── AC4: unique constraint prevents duplicate rows ──────────────────────────

  @Test
  void uniqueConstraintPreventsDuplicateLogRows() throws Exception {
    create("McAfee", "2026-11-08");

    runReminders();
    runReminders();

    int count = jdbc.queryForObject("SELECT count(*) FROM notification_log", Integer.class);
    assertThat(count).isEqualTo(1);
  }

  // ── Settings: GET returns server and topic-configured flag ──────────────────

  @Test
  void settingsEndpointReturnsServerAndTopicFlag() throws Exception {
    mvc.perform(get("/api/v1/settings/notifications"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.topicConfigured", is(true)))
        .andExpect(jsonPath("$.offsets").isArray());
  }

  // ── Settings: POST /test sends a notification ───────────────────────────────

  @Test
  void testNotificationEndpointSendsAndReturns204() throws Exception {
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                "/api/v1/settings/notifications/test"))
        .andExpect(status().isNoContent());

    ntfy.verify(moreThan(0), postRequestedFor(urlEqualTo("/")));
  }

  // ── Settings: POST /test returns 503 when ntfy topic not set ────────────────
  // (tested in unit layer via NotificationSettingsController with empty Optional)

  // ── helpers ─────────────────────────────────────────────────────────────────

  private org.springframework.test.web.servlet.ResultActions runReminders() throws Exception {
    return mvc.perform(post("/api/v1/jobs/reminders")).andExpect(status().isOk());
  }

  private long create(String name, String renewal) throws Exception {
    long categoryId =
        jdbc.queryForObject("SELECT id FROM category WHERE name = 'AI Tools'", Long.class);
    String body =
        """
        {"name":"%s","vendor":"Anthropic","categoryId":%d,"type":"PAID","billingCycle":"MONTHLY",
         "amount":"18.00","currency":"GBP","startDate":"2026-01-01","nextRenewalDate":"%s",
         "noticeDays":0,"autoRenew":true,"owner":"Me"}
        """
            .formatted(name, categoryId, renewal);
    String resp =
        mvc.perform(
                post("/api/v1/subscriptions").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return ((Number) JsonPath.read(resp, "$.id")).longValue();
  }
}
