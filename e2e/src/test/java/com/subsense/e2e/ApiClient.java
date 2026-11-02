package com.subsense.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;

/** Sets up test data through the public REST API, so scenarios start from a known state. */
final class ApiClient {

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private final String baseUrl;

  ApiClient(String baseUrl) {
    this.baseUrl = baseUrl + "/api/v1";
  }

  void refreshExchangeRates() {
    send("POST", "/fx-rates/refresh", null, 200);
  }

  void deleteAllSubscriptions() {
    for (JsonNode s : send("GET", "/subscriptions", null, 200)) {
      send("DELETE", "/subscriptions/" + s.get("id").asLong(), null, 204);
    }
  }

  long firstCategoryId() {
    return send("GET", "/categories", null, 200).get(0).get("id").asLong();
  }

  void createSubscription(
      String name, String cycle, String amount, String currency, LocalDate renewal) {
    String body =
        """
        {"name":"%s","vendor":"Example Ltd","categoryId":%d,"type":"PAID",
         "billingCycle":"%s","amount":"%s","currency":"%s",
         "startDate":"2025-01-01","nextRenewalDate":"%s","noticeDays":0,
         "autoRenew":true,"owner":"Me"}
        """
            .formatted(name, firstCategoryId(), cycle, amount, currency, renewal);
    send("POST", "/subscriptions", body, 201);
  }

  void createFreeTrial(
      String name, String cycle, String priceAfterTrial, String currency, LocalDate trialEnd) {
    String body =
        """
        {"name":"%s","vendor":"Example Ltd","categoryId":%d,"type":"FREE_TRIAL",
         "billingCycle":"%s","amount":"0.00","priceAfterTrial":"%s","currency":"%s",
         "startDate":"2025-01-01","nextRenewalDate":"%s","noticeDays":0,
         "autoRenew":true,"owner":"Me"}
        """
            .formatted(name, firstCategoryId(), cycle, priceAfterTrial, currency, trialEnd);
    send("POST", "/subscriptions", body, 201);
  }

  /** Test-only trigger, enabled in the e2e stack by SUBSENSE_JOBS_MANUAL_TRIGGER. */
  void runRollover() {
    send("POST", "/jobs/rollover", null, 200);
  }

  /** Test-only trigger; returns {"sent": n, "failed": n, "skipped": n}. */
  JsonNode runReminders() {
    return send("POST", "/jobs/reminders", null, 200);
  }

  private JsonNode send(String method, String path, String body, int expectedStatus) {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .header("Content-Type", "application/json")
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body))
            .build();
    try {
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != expectedStatus) {
        throw new IllegalStateException(
            method + " " + path + " returned " + response.statusCode() + ": " + response.body());
      }
      return response.body().isBlank() ? null : json.readTree(response.body());
    } catch (IOException e) {
      throw new IllegalStateException("API call failed: " + method + " " + path, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
