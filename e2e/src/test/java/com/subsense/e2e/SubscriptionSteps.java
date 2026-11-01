package com.subsense.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.LocalDate;
import java.time.ZoneId;

public class SubscriptionSteps {

  private static final String BASE_URL =
      System.getenv().getOrDefault("E2E_BASE_URL", "http://localhost:3000");
  private static final ZoneId ZONE = ZoneId.of("Europe/London");

  private final ApiClient api = new ApiClient(BASE_URL);
  private Playwright playwright;
  private Browser browser;
  private Page page;

  @Before
  public void openBrowser() {
    playwright = Playwright.create();
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    page = browser.newPage();
  }

  @After
  public void closeBrowser() {
    browser.close();
    playwright.close();
  }

  @Given("the register is empty and exchange rates are fixed")
  public void cleanState() {
    api.refreshExchangeRates();
    api.deleteAllSubscriptions();
  }

  @Given("a {string} subscription {string} costing {string} {string} renewing in {int} days")
  public void existingSubscription(
      String cycle, String name, String amount, String currency, int days) {
    api.createSubscription(name, cycle.toUpperCase(), amount, currency, inDays(days));
  }

  @When(
      "I add a {string} subscription {string} from {string} costing {string} {string}"
          + " renewing in {int} days")
  public void addThroughTheForm(
      String cycle, String name, String vendor, String amount, String currency, int days) {
    fillForm(name, vendor, amount, currency, cycle, inDays(days));
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save subscription"))
        .click();
    assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(name)))
        .isVisible();
  }

  @When("I try to add {string} renewing before it started")
  public void addWithRenewalBeforeStart(String name) {
    fillForm(name, "Example Ltd", "9.99", "GBP", "Monthly", LocalDate.of(2020, 1, 1));
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save subscription"))
        .click();
  }

  @When("I open the dashboard")
  public void openDashboard() {
    page.navigate(BASE_URL + "/");
  }

  @When("I cancel {string} from the dashboard")
  public void cancelFromDashboard(String name) {
    openDashboard();
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancel " + name)).click();
  }

  @Then("the subscriptions list shows {string} at {string}")
  public void listShows(String name, String price) {
    page.navigate(BASE_URL + "/subscriptions");
    Locator row = page.getByRole(AriaRole.ROW).filter(new Locator.FilterOptions().setHasText(name));
    assertThat(row).containsText(price);
  }

  @Then("I see the error {string}")
  public void seeError(String message) {
    assertThat(page.getByText(message)).isVisible();
  }

  @Then("monthly spend is {string}")
  public void monthlySpendIs(String amount) {
    assertThat(page.getByTestId("monthly-spend")).hasText(amount);
  }

  @Then("{string} no longer needs a decision")
  public void noLongerNeedsDecision(String name) {
    Locator panel =
        page.getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Needs a decision"));
    assertThat(panel.getByText(name)).not().isVisible();
  }

  @Then("{string} is shown as {string}")
  public void shownWithStatus(String name, String status) {
    listShows(name, status);
  }

  private void fillForm(
      String name, String vendor, String amount, String currency, String cycle, LocalDate renewal) {
    page.navigate(BASE_URL + "/subscriptions/new");
    page.getByLabel("Name").fill(name);
    page.getByLabel("Vendor").fill(vendor);
    page.getByLabel("Price", new Page.GetByLabelOptions().setExact(true)).fill(amount);
    page.getByLabel("Currency").selectOption(currency);
    page.getByLabel("Billed").selectOption(new String[] {cycle.toUpperCase()});
    page.getByLabel("Next renewal").fill(renewal.toString());
  }

  private static LocalDate inDays(int days) {
    return LocalDate.now(ZONE).plusDays(days);
  }
}
