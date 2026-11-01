Feature: Renewal rollover
  So that renewal dates stay correct without me editing them
  I want each renewal to move on, and my decisions to take effect, once its date arrives

  Background:
    Given the register is empty and exchange rates are fixed

  @F-04
  Scenario: A renewal that has passed moves to the next billing date
    Given a "Monthly" subscription "Spotify" costing "11.99" "GBP" that renewed yesterday
    When the daily renewal rollover runs
    Then "Spotify" renews one month after yesterday

  @F-04
  Scenario: A cancelled subscription is shown as Cancelled after its renewal date
    Given a "Monthly" subscription "Netflix" costing "10.99" "GBP" renewing in 0 days
    And I cancel "Netflix" from the dashboard
    When the daily renewal rollover runs
    Then "Netflix" is shown as "Cancelled"
    When I open the dashboard
    Then monthly spend is "£0.00"

  @F-04
  Scenario: A free trial that has ended becomes a paid plan
    Given a "Monthly" free trial "LinkedIn Premium" then costing "29.99" "GBP" that ended yesterday
    When the daily renewal rollover runs
    Then the subscriptions list shows "LinkedIn Premium" at "£29.99"
    And "LinkedIn Premium" is shown as "Active"
