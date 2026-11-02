package com.subsense.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class ReminderSteps {

  private static final String BASE_URL =
      System.getenv().getOrDefault("E2E_BASE_URL", "http://localhost:3000");
  private static final String NTFY_STUB_URL =
      System.getenv().getOrDefault("E2E_NTFY_STUB_URL", "http://localhost:8089");

  private final ApiClient api = new ApiClient(BASE_URL);
  private final NtfyStub ntfy = new NtfyStub(NTFY_STUB_URL);
  private JsonNode lastRun;

  @Given("ntfy notifications are configured")
  public void ntfyConfigured() {
    ntfy.reset();
  }

  @When("the reminder job runs")
  public void reminderJobRuns() {
    lastRun = api.runReminders();
  }

  @When("the reminder job runs again")
  public void reminderJobRunsAgain() {
    reminderJobRuns();
  }

  @Then("{int} push notification is sent with title {string}")
  public void pushesWithTitle(int count, String title) {
    assertThat(ntfy.pushesWithTitle(title)).isEqualTo(count);
  }

  @Then("{int} push notification is sent for {string}")
  public void pushesFor(int count, String name) {
    assertThat(ntfy.pushesMentioning(name)).isEqualTo(count);
  }

  @Then("no push notification is sent for {string}")
  public void noPushFor(String name) {
    pushesFor(0, name);
  }

  @Then("the push for {string} was delivered after {int} attempts")
  public void deliveredAfterAttempts(String name, int attempts) {
    assertThat(ntfy.pushesMentioning(name)).isEqualTo(attempts);
  }

  @Then("the reminder job reported {int} sent")
  public void jobReportedSent(int sent) {
    assertThat(lastRun.get("sent").asInt()).isEqualTo(sent);
    assertThat(lastRun.get("failed").asInt()).isZero();
  }
}
