# ADR 0003: Renewal rollover with an anchor day of month

- Status: Accepted
- Date: 2026-11-01

## Context

When a renewal date passes, the next one must be worked out and the user's decision applied (F-04). Monthly renewals on the 29th–31st must not drift: a renewal on 31 January should move to 28 February (29 in a leap year) and then back to 31 March. The app usually runs on a laptop that may be asleep at the scheduled time.

## Decision

- Store a **`renewal_anchor_day`** per subscription: the day of month the user chose. `BillingCycle.next` adds the cycle's months, then uses the anchor day, or the last day of shorter months. The anchor changes only when the user picks a new renewal date, not when a rollover lands on a shorter month.
- Store **`cancelled_on`** when a Cancel takes effect (on the renewal date) or a renewal lapses (no decision and auto-renew off). Status stays derived: `CANCELLED` whenever `cancelled_on` is set. Cancelled subscriptions are kept for reports, and the API rejects edits and decisions with 409.
- Renewals **advance the day after** the renewal date, so "renews today" stays visible all day.
- A **daily job** (00:15 in the configured time zone) also runs **on startup**. It catches up missed cycles in a loop, processes each subscription in its own transaction, and writes a `SYSTEM` entry to `decision_history` for every change. Running it again on the same day changes nothing.
- End-to-end tests trigger the job through `POST /api/v1/jobs/rollover`, which only exists when `subsense.jobs.manual-trigger=true` (end-to-end stack only).

## Consequences

- Month-end and leap-year rules live in one place (`BillingCycle`) and are unit tested without dates from the system clock.
- Cucumber scenarios run against the real date, so exact month-end dates (31 Jan, 29 Feb) are proved in unit and Testcontainers tests with a movable clock.
- Reactivating a cancelled subscription is not supported yet. Add it again as a new subscription.
