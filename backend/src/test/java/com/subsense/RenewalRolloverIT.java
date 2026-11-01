package com.subsense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.subsense.support.MutableClock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
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
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** F-04 against a real PostgreSQL, with a clock the test moves from day to day. */
@SpringBootTest(
    properties = {
      "subsense.fx.refresh-on-startup=false",
      "subsense.rollover.run-on-startup=false",
      "subsense.jobs.manual-trigger=true"
    })
@AutoConfigureMockMvc
@Testcontainers
class RenewalRolloverIT {

  private static final ZoneId LONDON = ZoneId.of("Europe/London");

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @TestConfiguration
  static class MovableClock {
    @Bean
    @Primary
    MutableClock movableClock() {
      return new MutableClock(Instant.parse("2026-11-01T09:00:00Z"), LONDON);
    }
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired MutableClock clock;
  @Autowired DataSource dataSource;

  @BeforeEach
  void emptyRegister() {
    clock.setDate(LocalDate.of(2026, 11, 1));
    jdbc.update("DELETE FROM decision_history");
    jdbc.update("DELETE FROM subscription");
  }

  @Test
  void migrationSetsAnchorDayFromExistingRenewalDates() {
    Flyway upTo = Flyway.configure().dataSource(dataSource).schemas("before_v3").target("2").load();
    upTo.migrate();
    jdbc.update(
        "INSERT INTO before_v3.subscription (name, vendor, category_id, type, billing_cycle,"
            + " amount, currency, start_date, next_renewal_date)"
            + " SELECT 'Netflix', 'Netflix', id, 'PAID', 'MONTHLY', 10.99, 'GBP',"
            + " DATE '2026-01-31', DATE '2026-12-31' FROM before_v3.category LIMIT 1");

    Flyway.configure().dataSource(dataSource).schemas("before_v3").load().migrate();

    assertThat(
            jdbc.queryForObject(
                "SELECT renewal_anchor_day FROM before_v3.subscription", Integer.class))
        .isEqualTo(31);
  }

  @Test
  void monthlyRenewalOn31JanMovesTo28FebThen31Mar() throws Exception {
    long id = create("2027-01-31");

    decide(id, "RENEW");
    rollOverOn(LocalDate.of(2027, 2, 1));
    renewalIs(id, "2027-02-28");

    decide(id, "RENEW");
    rollOverOn(LocalDate.of(2027, 3, 1));
    renewalIs(id, "2027-03-31");
  }

  @Test
  void monthlyRenewalOn31JanMovesTo29FebInALeapYear() throws Exception {
    long id = create("2028-01-31");

    decide(id, "RENEW");
    rollOverOn(LocalDate.of(2028, 2, 1));
    renewalIs(id, "2028-02-29");

    decide(id, "RENEW");
    rollOverOn(LocalDate.of(2028, 3, 1));
    renewalIs(id, "2028-03-31");
  }

  @Test
  void cancelledSubscriptionIsCancelledOnItsRenewalDateAndCannotBeChanged() throws Exception {
    long id = create("2026-11-20");
    decide(id, "CANCEL");

    rollOverOn(LocalDate.of(2026, 11, 20));

    mvc.perform(get("/api/v1/subscriptions/" + id))
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.cancelledOn").value("2026-11-20"));
    mvc.perform(
            post("/api/v1/subscriptions/" + id + "/decisions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"RENEW\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Subscription is cancelled"));
    mvc.perform(
            put("/api/v1/subscriptions/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(subscription("2026-11-20")))
        .andExpect(status().isConflict());
    mvc.perform(get("/api/v1/subscriptions/" + id + "/decisions"))
        .andExpect(jsonPath("$[0].source").value("SYSTEM"))
        .andExpect(jsonPath("$[0].reason").value("Cancelled on 2026-11-20"));
  }

  @Test
  void runningTwiceOnTheSameDayChangesNothingMore() throws Exception {
    long id = create("2026-11-15");
    clock.setDate(LocalDate.of(2026, 11, 16));

    trigger().andExpect(jsonPath("$.processed").value(1));
    trigger().andExpect(jsonPath("$.processed").value(0));

    renewalIs(id, "2026-12-15");
    mvc.perform(get("/api/v1/subscriptions/" + id + "/decisions"))
        .andExpect(jsonPath("$", hasSize(1)));
  }

  @Test
  void catchUpHistoryListsTheLatestRenewalFirst() throws Exception {
    long id = create("2026-09-15");

    rollOverOn(LocalDate.of(2026, 11, 1));

    // Both entries share one timestamp, so the order must not depend on it alone.
    mvc.perform(get("/api/v1/subscriptions/" + id + "/decisions"))
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].reason").value("Renewed on 2026-10-15; next renewal 2026-11-15"))
        .andExpect(jsonPath("$[1].reason").value("Renewed on 2026-09-15; next renewal 2026-10-15"));
  }

  private void rollOverOn(LocalDate date) throws Exception {
    clock.setDate(date);
    trigger();
  }

  private org.springframework.test.web.servlet.ResultActions trigger() throws Exception {
    return mvc.perform(post("/api/v1/jobs/rollover")).andExpect(status().isOk());
  }

  private void renewalIs(long id, String date) throws Exception {
    mvc.perform(get("/api/v1/subscriptions/" + id))
        .andExpect(jsonPath("$.nextRenewalDate").value(date))
        .andExpect(jsonPath("$.decision").value("PENDING"));
  }

  private void decide(long id, String decision) throws Exception {
    mvc.perform(
            post("/api/v1/subscriptions/" + id + "/decisions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"" + decision + "\"}"))
        .andExpect(status().isOk());
  }

  private long create(String renewal) throws Exception {
    String response =
        mvc.perform(
                post("/api/v1/subscriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(subscription(renewal)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return ((Number) JsonPath.read(response, "$.id")).longValue();
  }

  private String subscription(String renewal) {
    long categoryId =
        jdbc.queryForObject("SELECT id FROM category WHERE name = 'AI Tools'", Long.class);
    return """
    {
      "name": "Claude Pro",
      "vendor": "Anthropic",
      "categoryId": %d,
      "type": "PAID",
      "billingCycle": "MONTHLY",
      "amount": "18.00",
      "currency": "GBP",
      "startDate": "2026-01-01",
      "nextRenewalDate": "%s",
      "noticeDays": 0,
      "autoRenew": true,
      "owner": "Me"
    }
    """
        .formatted(categoryId, renewal);
  }
}
