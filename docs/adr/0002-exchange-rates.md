# ADR 0002: Exchange rates from the Frankfurter API, cached daily

- Status: Accepted
- Date: 2026-11-01

## Context

Subscriptions are billed in GBP, INR and USD, but totals and budgets need one reporting currency. Rates must be free, need no API key, and the app must still work when the rate service is down.

## Decision

- Use the **Frankfurter API** (European Central Bank reference rates), which covers GBP, INR and USD.
- Fetch **once a day** (06:00) and on startup; store rates **per euro** in `fx_rate`, so any pair converts through EUR.
- If a refresh fails, keep using the **latest cached day** and show its date in the UI ("rates as of …").
- Seed a fallback set of rates in a Flyway migration so a fresh install works offline.

## Consequences

- Totals are indicative (ECB reference rates, not the rate the card issuer applied). The payment log in Phase 3 records actual charges.
- End-to-end tests point the backend at a WireMock stub with fixed rates, so expected totals are exact.
