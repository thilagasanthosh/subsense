package com.subsense;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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

/**
 * Full stack against a real PostgreSQL (Testcontainers), with Flyway migrations, a fixed clock of 1
 * Nov 2026, and the seed exchange rates (per euro: GBP 0.86, INR 100, USD 1.16).
 */
@SpringBootTest(properties = "subsense.fx.refresh-on-startup=false")
@AutoConfigureMockMvc
@Testcontainers
class SubscriptionApiIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @TestConfiguration
  static class FixedClock {
    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(Instant.parse("2026-11-01T09:00:00Z"), ZoneId.of("Europe/London"));
    }
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void emptyRegister() {
    jdbc.update("DELETE FROM decision_history");
    jdbc.update("DELETE FROM subscription");
  }

  @Test
  void createsASubscriptionAndShowsItsDerivedValues() throws Exception {
    mvc.perform(
            post("/api/v1/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(subscription("Claude Pro", "18.00", "GBP", "MONTHLY", "2026-11-15", 3)))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.price.amount").value("18.00"))
        .andExpect(jsonPath("$.cancelByDate").value("2026-11-12"))
        .andExpect(jsonPath("$.daysUntilRenewal").value(14))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.category.name").value("AI Tools"));

    mvc.perform(get("/api/v1/subscriptions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].name").value("Claude Pro"));
  }

  @Test
  void convertsAForeignCurrencyToTheReportingCurrency() throws Exception {
    // ₹1,200 a year = ₹100 a month = €1 = £0.86
    mvc.perform(
            post("/api/v1/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    subscription("Streaming India", "1200.00", "INR", "YEARLY", "2027-01-10", 0)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.monthlyEquivalent.amount").value("100.00"))
        .andExpect(jsonPath("$.monthlyEquivalent.currency").value("INR"))
        .andExpect(jsonPath("$.monthlyEquivalentReporting.amount").value("0.86"))
        .andExpect(jsonPath("$.monthlyEquivalentReporting.currency").value("GBP"));
  }

  @Test
  void rejectsARenewalDateBeforeTheStartDate() throws Exception {
    mvc.perform(
            post("/api/v1/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(subscription("Netflix", "10.99", "GBP", "MONTHLY", "2025-12-31", 0)))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.errors.nextRenewalDate").value("must be on or after the start date"));

    mvc.perform(get("/api/v1/subscriptions")).andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void rejectsACardNumberAsThePaymentLabel() throws Exception {
    String body =
        subscription("Netflix", "10.99", "GBP", "MONTHLY", "2026-12-01", 0)
            .replace("\"Visa\"", "\"4111 1111 1111 1111\"");

    mvc.perform(post("/api/v1/subscriptions").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.paymentLabel").exists());
  }

  @Test
  void softDeleteRemovesTheSubscriptionFromTheRegister() throws Exception {
    long id = create(subscription("McAfee", "89.99", "GBP", "YEARLY", "2027-03-01", 0));

    mvc.perform(delete("/api/v1/subscriptions/" + id)).andExpect(status().isNoContent());

    mvc.perform(get("/api/v1/subscriptions")).andExpect(jsonPath("$", hasSize(0)));
    mvc.perform(get("/api/v1/subscriptions/" + id)).andExpect(status().isNotFound());
  }

  @Test
  void aCancelDecisionIsRecordedAndChangesTheStatus() throws Exception {
    long id = create(subscription("McAfee", "89.99", "GBP", "YEARLY", "2026-11-20", 0));

    mvc.perform(
            post("/api/v1/subscriptions/" + id + "/decisions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"decision\":\"CANCEL\",\"reason\":\"Switching to built-in protection\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.decision").value("CANCEL"))
        .andExpect(jsonPath("$.status").value("CANCELLING"));

    mvc.perform(get("/api/v1/subscriptions/" + id + "/decisions"))
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].source").value("UI"))
        .andExpect(jsonPath("$[0].reason").value("Switching to built-in protection"));
  }

  @Test
  void dashboardTotalsEverythingInTheReportingCurrency() throws Exception {
    // £10.00 + ₹100 (= £0.86) + $1.16 (= £0.86) = £11.72 a month; the INR one renews in 5 days
    create(subscription("Netflix", "10.00", "GBP", "MONTHLY", "2026-12-20", 0));
    create(subscription("Mobile plan", "100.00", "INR", "MONTHLY", "2026-11-06", 0));
    create(subscription("ChatGPT Plus", "1.16", "USD", "MONTHLY", "2026-12-25", 0));

    mvc.perform(get("/api/v1/dashboard/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reportingCurrency").value("GBP"))
        .andExpect(jsonPath("$.monthlySpend.amount").value("11.72"))
        .andExpect(jsonPath("$.annualSpend.amount").value("140.64"))
        .andExpect(jsonPath("$.activeCount").value(3))
        .andExpect(jsonPath("$.fxRatesAsOf").value("2026-01-01"))
        .andExpect(jsonPath("$.needsDecision", hasSize(1)))
        .andExpect(jsonPath("$.needsDecision[0].name").value("Mobile plan"));
  }

  @Test
  void listsTheSeededCategories() throws Exception {
    mvc.perform(get("/api/v1/categories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(7)))
        .andExpect(jsonPath("$[0].name").value("AI Tools"));
  }

  private long create(String body) throws Exception {
    String response =
        mvc.perform(
                post("/api/v1/subscriptions").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return ((Number) JsonPath.read(response, "$.id")).longValue();
  }

  private String subscription(
      String name, String amount, String currency, String cycle, String renewal, int noticeDays) {
    long categoryId =
        jdbc.queryForObject("SELECT id FROM category WHERE name = 'AI Tools'", Long.class);
    return """
    {
      "name": "%s",
      "vendor": "Example Ltd",
      "categoryId": %d,
      "type": "PAID",
      "billingCycle": "%s",
      "amount": "%s",
      "currency": "%s",
      "startDate": "2026-01-01",
      "nextRenewalDate": "%s",
      "noticeDays": %d,
      "autoRenew": true,
      "paymentLabel": "Visa",
      "owner": "Me"
    }
    """
        .formatted(name, categoryId, cycle, amount, currency, renewal, noticeDays);
  }
}
