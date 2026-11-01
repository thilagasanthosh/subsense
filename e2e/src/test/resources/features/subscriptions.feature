Feature: Subscription register
  So that every recurring payment is in one place
  I want to add subscriptions in any of my currencies and see what they cost me

  Background:
    Given the register is empty and exchange rates are fixed

  @F-01
  Scenario: Add a monthly subscription through the form
    When I add a "Monthly" subscription "Claude Pro" from "Anthropic" costing "18.00" "GBP" renewing in 10 days
    Then the subscriptions list shows "Claude Pro" at "£18.00"

  @F-01
  Scenario: A renewal date before the start date is rejected
    When I try to add "Netflix" renewing before it started
    Then I see the error "Must be on or after the start date"

  @F-02
  Scenario: Spend in rupees and dollars is totalled in pounds
    # Fixed rates per euro: GBP 0.85, INR 90, USD 1.10
    Given a "Monthly" subscription "Netflix" costing "10.00" "GBP" renewing in 20 days
    And a "Monthly" subscription "Mobile plan" costing "900.00" "INR" renewing in 20 days
    And a "Monthly" subscription "ChatGPT Plus" costing "11.00" "USD" renewing in 20 days
    When I open the dashboard
    Then monthly spend is "£27.00"

  @F-04
  Scenario: Decide to cancel a renewal from the dashboard
    Given a "Monthly" subscription "Netflix" costing "10.99" "GBP" renewing in 5 days
    When I cancel "Netflix" from the dashboard
    Then "Netflix" no longer needs a decision
    And "Netflix" is shown as "Cancelling"
