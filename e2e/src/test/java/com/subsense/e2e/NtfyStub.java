package com.subsense.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The WireMock container that stands in for ntfy, read through WireMock's admin API so scenarios
 * can count the pushes the backend sent (JSON posted to the server root, topic "subsense-e2e").
 */
final class NtfyStub {

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private final String adminUrl;

  NtfyStub(String baseUrl) {
    this.adminUrl = baseUrl + "/__admin";
  }

  /** Forgets earlier requests and restarts the "503 then 200" scenario. */
  void reset() {
    send("DELETE", "/requests", null);
    send("POST", "/scenarios/reset", null);
  }

  int pushesWithTitle(String title) {
    return count(Map.of("equalTo", title));
  }

  int pushesMentioning(String name) {
    return count(Map.of("contains", name));
  }

  private int count(Map<String, Object> titleMatcher) {
    Map<String, Object> title = new HashMap<>(titleMatcher);
    title.put("expression", "$.title");
    Map<String, Object> pattern =
        Map.of(
            "method",
            "POST",
            "url",
            "/",
            "bodyPatterns",
            List.of(Map.of("matchesJsonPath", title)));
    try {
      String body = send("POST", "/requests/count", json.writeValueAsString(pattern));
      return json.readTree(body).get("count").asInt();
    } catch (IOException e) {
      throw new IllegalStateException("Could not read the ntfy stub", e);
    }
  }

  private String send(String method, String path, String body) {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(adminUrl + path))
            .header("Content-Type", "application/json")
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body))
            .build();
    try {
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 300) {
        throw new IllegalStateException(
            method + " " + path + " on the ntfy stub returned " + response.statusCode());
      }
      return response.body();
    } catch (IOException e) {
      throw new IllegalStateException("ntfy stub not reachable at " + adminUrl, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
