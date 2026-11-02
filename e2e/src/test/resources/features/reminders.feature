Feature: Renewal reminders
  So that I never pay for a subscription I meant to cancel
  I want a push notification on my phone before the cancel-by date

  Background:
    Given the register is empty and exchange rates are fixed
    And ntfy notifications are configured

  @F-05
  Scenario: Reminder sent seven days before the cancel-by date
    Given a "Monthly" subscription "Claude Pro" costing "18.00" "GBP" renewing in 7 days
    When the reminder job runs
    Then 1 push notification is sent with title "Claude Pro renews in 7 days"

  @F-05
  Scenario: Reminder is not sent twice
    Given a "Monthly" subscription "Netflix" costing "10.99" "GBP" renewing in 7 days
    When the reminder job runs
    And the reminder job runs again
    Then 1 push notification is sent for "Netflix"

  @F-05
  Scenario: A busy ntfy server is retried
    # The stub answers 503 to the first "Spotify" push, then 200.
    Given a "Monthly" subscription "Spotify" costing "11.99" "GBP" renewing in 7 days
    When the reminder job runs
    Then the push for "Spotify" was delivered after 2 attempts
    And the reminder job reported 1 sent

  @F-05
  Scenario: No reminder once I have decided to cancel
    Given a "Yearly" subscription "McAfee" costing "89.99" "GBP" renewing in 7 days
    And I cancel "McAfee" from the dashboard
    When the reminder job runs
    Then no push notification is sent for "McAfee"
