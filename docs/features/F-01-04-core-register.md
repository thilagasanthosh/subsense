# Plan: Phase 1, core register (F-01, F-02, F-03, basic F-04)

Status: Approved · Implemented

## Scope

- **In:** subscription CRUD with soft delete (F-01); GBP/INR/USD with a reporting currency and cached ECB rates (F-02); dashboard cards and spend-by-category donut (F-03); recording Renew / Cancel / Downgrade decisions with an audit trail (basic F-04); Renewals timeline page.
- **Out (later phases):** renewal rollover and month-end date maths (F-04, Phase 2), ntfy reminders (F-05), budgets (F-06), reviews and savings (F-07), Settings page, 12-month projection chart.

## API

`/api/v1/subscriptions` (GET list, POST, GET/PUT/DELETE by id), `POST/GET /subscriptions/{id}/decisions`, `GET /dashboard/summary`, `GET /categories`, `GET /fx-rates`, `POST /fx-rates/refresh`. Errors are RFC 7807 problem details with `errors: {field: message}`.

## Schema

- `V1__init.sql`: `category`, `subscription` (with `CHECK` constraints for amounts, notice days, and renewal ≥ start), `decision_history`, `fx_rate`.
- `V2__seed_reference_data.sql`: seven categories and fallback exchange rates.

## Domain rules and edge cases

- Monthly equivalent: monthly ÷ 1, quarterly ÷ 3, yearly ÷ 12; free trials use the price after the trial.
- Cancel-by date = renewal date − notice days.
- Status: `CANCELLING` once the decision is Cancel; otherwise `TRIAL` or `ACTIVE`.
- Dashboard spend excludes subscriptions being cancelled.
- Rounding (`HALF_EVEN`, 2 dp) only when presenting money.
- Payment label allows at most 4 digits, so a card number can never be stored.
- Rate service down: keep the last cached day and show its date.

## Design

- `BillingCycle` owns its cost maths (open/closed); `Money` and `ExchangeRates` are immutable value types; `FxRateProvider` is a small interface with a Frankfurter implementation (dependency inversion).
- `DashboardService.summarise` is a pure function of subscriptions, rates and today, so it is unit tested without Spring.
- `Clock` is injected everywhere "today" matters.
- Status is derived, not stored, until the Phase 2 rollover needs `CANCELLED` (YAGNI).

## Tests (in order written)

1. Unit: `MoneyTest`, `ExchangeRatesTest`, `BillingCycleTest`, `SubscriptionTest`, `SubscriptionServiceTest`, `FxRateServiceTest`, `FrankfurterFxRateProviderTest`, `DashboardServiceTest`.
2. Integration (Testcontainers PostgreSQL): `SubscriptionApiIT`.
3. Frontend (Vitest + Testing Library + MSW): formatting, form rules, Dashboard, Subscriptions and Form pages.
4. End-to-end (Cucumber + Playwright): `subscriptions.feature` tagged `@F-01`, `@F-02`, `@F-04`.

## New dependencies

springdoc-openapi (API docs), Testcontainers, Recharts, React Hook Form, Zod, TanStack Query, MSW, Cucumber, Playwright.
