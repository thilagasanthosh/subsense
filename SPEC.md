# SubSense: Personal Subscription Spend Manager

**Master specification** · Version 1.1 · Status: Phase 1 implemented

SubSense keeps every personal subscription in one place: what it costs, when it renews, whether it is still worth paying for, and a reminder on your phone before money leaves your account. It replaces scattered calendar reminders and bank-statement surprises with a single view of recurring spend across currencies.

---

## 1. Goals and non-goals

### Goals

- One register of all subscriptions: paid, free trials, monthly, quarterly and yearly.
- Know the true monthly and annual cost, across **GBP, INR and USD**, in one reporting currency.
- Never miss a renewal or a trial ending: push reminders to the phone before the "cancel by" date.
- Make a deliberate **Renew / Cancel / Downgrade** decision for every renewal.
- Spot subscriptions that are no longer used, and track the savings from cancelling them.
- Stay within a monthly budget per category.
- **Talk to my data:** ask questions in plain language from an AI client (via an MCP server) instead of searching in the UI.

### Non-goals (for now)

- Connecting to bank accounts or reading card statements.
- Cancelling subscriptions on the vendor's site automatically.
- Multi-user / household accounts (single user until Phase 3).
- A native mobile app (the phone is reached through push notifications; the UI is responsive).

---

## 2. Example subscriptions this must handle

| Subscription | Cycle | Currency | Special case |
|---|---|---|---|
| Amazon Prime | Yearly | GBP | |
| Claude Pro | Monthly | GBP | |
| ChatGPT Plus | Monthly | USD | Billed in a foreign currency |
| LinkedIn Premium | Monthly | GBP | Often starts as a **free trial** |
| Netflix | Monthly | GBP | |
| McAfee | Yearly | GBP | Auto-renews at a higher price |
| TV Licence | **Quarterly** | GBP | |
| Indian streaming / mobile plan | Monthly | **INR** | Indian number format (₹1,23,456) |

Seed data in the repository uses **fictional sample values**, never real personal data.

---

## 3. Feature specification

Each feature has an ID used in branch names, commits, PRs and Cucumber tags (for example `@F-02`).

### F-01 Subscription register

The central list of all subscriptions.

**Fields**

| Field | Type | Rules |
|---|---|---|
| Name | text | Required, max 100 chars |
| Vendor | text | Required |
| Category | reference | Required. Defaults: AI Tools, Entertainment, Security, Career, Shopping, Household, Utilities |
| Type | enum | `PAID`, `FREE_TRIAL` |
| Billing cycle | enum | `MONTHLY`, `QUARTERLY`, `YEARLY` |
| Amount | decimal | Required, ≥ 0, 2 decimal places. A free trial may be 0 with a "price after trial" |
| Price after trial | decimal | Required when type is `FREE_TRIAL` |
| Currency | enum | `GBP`, `INR`, `USD` (extensible) |
| Start date | date | Required |
| Next renewal / expiry date | date | Required. Must be ≥ start date |
| Notice period (days) | int | Default 0, max 365 |
| Auto-renews | boolean | Default true |
| Payment method label | text | A label only, e.g. "Visa", "PayPal". **Never card or account numbers** |
| Owner | text | Default "Me". Who the subscription is for |
| Status | enum | `TRIAL`, `ACTIVE`, `CANCELLING`, `CANCELLED` (derived, see F-04) |
| Usage rating | enum | `DAILY`, `WEEKLY`, `MONTHLY`, `RARELY`, `NEVER` |
| Notes | text | Optional, max 1000 chars |

**Derived values**

- **Monthly equivalent cost:** monthly × 1, quarterly ÷ 3, yearly ÷ 12.
- **Annual cost:** monthly equivalent × 12.
- **Cancel-by date:** next renewal date − notice period.
- **Days until renewal:** from today (Europe/London by default, configurable).

**Acceptance criteria**

- Given valid input, when I save, then the subscription appears in the list with its derived values.
- Given a renewal date before the start date, when I save, then I see a validation error and nothing is saved.
- Given a free trial with no "price after trial", when I save, then I see a validation error.
- When I delete a subscription, I am asked to confirm, and it is soft-deleted (kept for reports).

### F-02 Multi-currency

- Each subscription is stored in its **own currency** and always displayed in it.
- A **reporting currency** (setting, default GBP) is used for totals, dashboards and budgets.
- Exchange rates come from the free **Frankfurter API** (European Central Bank reference rates, includes GBP, INR and USD), fetched once a day and cached in the `fx_rate` table.
- If the rate service is unavailable, the last cached rate is used and the UI shows "rates as of <date>".
- A manual rate override is available in Settings.
- Money is stored as `NUMERIC(12,2)` and handled as `BigDecimal` in Java. **Never `double`.** Rounding is `HALF_EVEN`, applied only for display and totals.
- The UI formats amounts with `Intl.NumberFormat`: `en-GB` for GBP, `en-IN` for INR (₹1,23,456.00), `en-US` for USD.

**Acceptance criteria**

- Given subscriptions of £10.00, ₹649.00 and $20.00 and reporting currency GBP, the dashboard total equals the sum of each converted at the cached rate.
- Given the rate service is down, totals still render using the last cached rate, with its date shown.

### F-03 Dashboard

- Summary cards: **monthly spend**, **projected annual spend**, **active subscriptions**, **renewals in the next 30 days**, **potential savings** (sum of underused subscriptions, F-07).
- Chart: spend by category (donut).
- Chart: projected spend for the next 12 months (bar), based on renewal dates and cycles.
- "Needs a decision" list: renewals and trial endings in the next 14 days with **Renew / Cancel** buttons.

### F-04 Renewal decisions and lifecycle

- Decisions: `PENDING` (default), `RENEW`, `CANCEL`, `DOWNGRADE` (with new amount).
- Every decision is recorded in `decision_history` with timestamp and optional reason (audit trail).
- **Daily rollover job** (after midnight, configured time zone):
  - Renewal date passed and decision `RENEW` or `PENDING` with auto-renew on → advance the date by one cycle, reset decision to `PENDING`.
  - Decision `CANCEL` → status `CANCELLED` on the renewal date.
  - Decision `DOWNGRADE` → apply the new amount and advance the date.
  - Free trial end date passed and not cancelled → type becomes `PAID`, amount becomes "price after trial", status `ACTIVE`.
- Status `CANCELLING` = decision `CANCEL` made but renewal date not yet reached.
- Month-end handling: a renewal on 31 January advances to 28/29 February, then 31 March (use the original day of month where it exists).

**Acceptance criteria**

- Given a monthly subscription renewing 31 Jan with decision RENEW, after rollover the next renewal is 28 Feb (29 in a leap year), and after the next rollover it is 31 Mar.
- Given decision CANCEL and the renewal date passes, status becomes CANCELLED and no further reminders are sent.

### F-05 Renewal reminders (ntfy push notifications)

Reminders arrive on the phone through **[ntfy](https://ntfy.sh)**, a free, open-source push notification service. The ntfy app (Android/iOS) subscribes to a private topic; the backend sends an HTTP POST.

- **When:** a scheduled job runs daily at 08:00 (configurable) and finds subscriptions whose **cancel-by date** or **trial end** is exactly **14, 7 or 1 day(s)** away (offsets configurable), plus "renews today".
- **Message:** title "Claude Pro renews in 7 days", body with amount in native currency, cancel-by date and current decision; a click URL opening the subscription in SubSense; priority higher for 1-day reminders.
- **Only for** status `TRIAL` / `ACTIVE` with decision `PENDING` (no reminder once a decision is made, except a "renews today" confirmation).
- **Configuration:** ntfy server URL (default `https://ntfy.sh`, self-hosting supported), topic name, optional access token. The topic is generated as a long random string, because anyone who knows a public topic name can read it.
- **Exactly once:** `notification_log` has a unique key on `(subscription_id, renewal_date, offset_days)`. If the job runs twice or restarts mid-way, a reminder is never sent twice.
- **Reliability:** retry on HTTP 429 / 5xx with exponential backoff (3 attempts); a reminder is logged as `SENT` only after a 2xx response, otherwise `FAILED` and retried on the next run.
- A **"Send test notification"** button in Settings.

**Acceptance criteria**

- Given a subscription with cancel-by date in 7 days and decision PENDING, when the job runs, one notification is sent and logged.
- Given the job runs twice the same day, the notification is sent only once.
- Given ntfy returns 503 then 200, the notification is sent once and logged SENT.
- Given decision CANCEL, no reminder is sent.

### F-06 Budgets

- Monthly budget per category, in the reporting currency.
- Budget page: budget vs actual (monthly equivalent) per category, variance, year-to-date.
- Over-budget categories highlighted; optional ntfy alert when a new subscription pushes a category over budget.

### F-07 "Still using it?" reviews

- Every subscription gets a **review prompt** every 90 days (configurable) and 7 days before a yearly renewal.
- The review asks: how often do you use it (usage rating), and keep or cancel?
- A subscription is **underused** if usage is `RARELY` or `NEVER`, or it has not been reviewed for 180 days.
- Underused subscriptions feed the dashboard "potential savings" card.
- **Savings tracker:** cancelling records the annual amount saved, shown as "saved this year".

### F-08 Payment log and price-change detection (Phase 3)

- Record each actual charge (date, amount, currency); prefilled from the expected amount on renewal.
- If an actual charge differs from the expected amount by more than 1%, flag a **price change** and keep price history.

### F-09 Import, export and calendar feed (Phase 3)

- CSV import with a preview and row-level validation errors; CSV export of all subscriptions.
- A private **iCalendar (.ics) feed** URL of renewals and cancel-by dates, for subscribing in Google / Apple / Outlook calendars.

### F-10 Documents (Phase 3)

- Attach receipts and confirmation PDFs/images to a subscription (stored on disk or S3-compatible storage; max 5 MB each).

### F-11 Talk to my data: SubSense MCP server (Phase 4)

The web UI stays the main way to add, edit and review subscriptions. The MCP server is a second, conversational way into the same data: instead of searching and filtering in the UI, ask questions in plain language from any **Model Context Protocol (MCP)** client, such as Claude Desktop or Claude Code:

- "What renews in the next 30 days, and how much will it cost in total?"
- "How much do I spend on AI tools per year, in GBP?"
- "Which subscriptions haven't I used much? How much would I save by cancelling them?"
- "Am I over budget on entertainment this month?"
- "List everything I pay for in INR."

**Design**

- A separate module, `mcp-server/`, built with the **Spring AI MCP server** starter.
- It calls the SubSense **REST API** (`/api/v1/...`) through the generated client. It never reads the database directly, so all business rules (cycle maths, currency conversion, underused logic) stay in one place in the backend (DRY), and the MCP server stays a thin adapter (single responsibility).
- Transports: **STDIO** first, for Claude Desktop on the same machine; **Streamable HTTP** later if the app is hosted.
- The AI client's model does the reasoning and wording. The MCP server only returns accurate, structured data.

**Tools (read-only, Phase 4a)**

| Tool | Inputs | Returns |
|---|---|---|
| `list_subscriptions` | optional category, status, currency, billing cycle, owner | Subscriptions with amount, currency, cycle, next renewal, status, decision |
| `get_subscription` | name or id | Full detail including decision and review history |
| `get_upcoming_renewals` | days (default 30, max 365) | Renewals and trial endings in the window, with cancel-by dates and a converted total |
| `get_spend_summary` | reporting currency (optional), group by: category / currency / owner | Monthly and annual totals, with the FX rate date used |
| `get_underused_subscriptions` | none | Underused subscriptions and the potential annual saving |
| `get_budget_status` | month (optional) | Budget vs actual per category, with variance |

- Every tool has a clear description and a JSON input schema, so the model chooses the right tool.
- Amounts are returned with their currency and as strings (no floating-point rounding).
- Each response includes the "as of" date and FX rate date, so answers are explicit about freshness.

**Resources and prompts**

- Resource `subsense://subscriptions`: the current register, for clients that attach context.
- Prompt `monthly-review`: walks through upcoming renewals and underused items, and asks keep or cancel for each.

**Write tools (Phase 4b, after read-only is proven)**

- `record_decision` (renew / cancel / downgrade) and `record_review` (usage rating).
- Human in the loop: the MCP client asks the user to approve each write call; the tool repeats back exactly what will change; every call is written to the `decision_history` audit trail with source `MCP`.
- No delete tool, and no tool that changes settings or credentials.

**Security and privacy**

- Read-only by default; write tools must be switched on explicitly in configuration.
- The MCP server authenticates to the backend with its own API token (scoped read-only unless writes are enabled).
- Runs locally over STDIO, so data only goes to the AI client the user has chosen to connect.
- Tool calls are logged (tool name, inputs, outcome, duration), never including the token.

**Acceptance criteria**

- Given three subscriptions renewing within 30 days, when an MCP client calls `get_upcoming_renewals` with days 30, then all three are returned with cancel-by dates and a total in the reporting currency.
- Given subscriptions in GBP, INR and USD, when `get_spend_summary` is called grouped by currency, then each currency's native total and the converted grand total are returned with the FX rate date.
- Given write tools are disabled, when the client lists tools, then `record_decision` is not offered.
- Given the backend is unavailable, when any tool is called, then a clear error is returned, not partial data.

**Testing**

- Unit tests for each tool handler with a fake API client.
- Integration tests using the Spring AI **MCP client** against the server with WireMock standing in for the backend API: list tools, call each tool, check the schema and result.
- A Cucumber scenario (`@F-11`) runs an MCP client against the full Docker Compose stack.

**Example Claude Desktop configuration** (documented in the README)

```json
{
  "mcpServers": {
    "subsense": {
      "command": "java",
      "args": ["-jar", "/path/to/subsense-mcp.jar"],
      "env": {
        "SUBSENSE_API_URL": "http://localhost:8080",
        "SUBSENSE_API_TOKEN": "<read-only token>"
      }
    }
  }
}
```

### F-12 Authentication (Phase 3)

- Single-user login with Spring Security (session cookie, BCrypt password). Required before the app is exposed beyond `localhost`.

---

## 4. UI specification (React + TypeScript)

Simple, clean and responsive. Left sidebar on desktop, bottom navigation on mobile.

| Page | Route | Contents |
|---|---|---|
| Dashboard | `/` | Summary cards, category donut, 12-month bar chart, "Needs a decision" list |
| Subscriptions | `/subscriptions` | Table: name, vendor, category, amount (native currency), cycle, monthly equivalent (reporting currency), renews in (red ≤ 7 days, amber ≤ 30, green), status chip, decision. Search, filters (category, cycle, status, currency, owner), sort |
| Add / Edit | `/subscriptions/new`, `/subscriptions/:id/edit` | Form with all F-01 fields; trial fields shown only for free trials; live preview of monthly equivalent and cancel-by date |
| Detail | `/subscriptions/:id` | Terms, decision buttons, decision history, review history; later payments and documents |
| Renewals | `/renewals` | Timeline of the next 90 days grouped by week; renewals and trial endings |
| Budgets | `/budgets` | Budget vs actual per category (Phase 2) |
| Settings | `/settings` | Reporting currency, time zone, ntfy server/topic/token, reminder offsets, review interval, FX overrides, test notification |

**Frontend libraries:** Vite, React Router, TanStack Query (server state), React Hook Form + Zod (forms and validation, schemas shared with API types), Recharts (charts), plain CSS with design tokens (see ADR 0001). API types mirror the backend OpenAPI spec (`/v3/api-docs`); generating them with `openapi-typescript` is a later improvement.

**Accessibility:** all form fields labelled, keyboard navigable, colour never the only signal (status chips include text).

---

## 5. Backend specification (Spring Boot)

### Stack

Java 21 · Spring Boot 3 · Spring Web · Spring Data JPA · Bean Validation · Flyway · PostgreSQL 16 · springdoc-openapi · Spring `RestClient` for ntfy and FX calls.

### Architecture

Package by feature, with a thin layered structure inside each:

```
com.subsense
├── subscription   (controller, service, domain, repository, dto)
├── decision
├── currency       (fx rates, money conversion)
├── notification   (reminder job, ntfy client, notification_log)
├── budget
├── review
├── dashboard
├── settings
└── common         (errors, clock, money types)
```

- Design follows **SOLID, DRY, KISS and YAGNI** (detailed in `CLAUDE.md`). Prefer the simplest design that meets the current feature.
- Business rules live in the domain/service layer, not controllers.
- All date logic uses an injected `java.time.Clock`, so tests can control "today".
- Errors are returned as RFC 7807 `ProblemDetail` with field-level validation messages.

### REST API (v1)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/subscriptions` | List, with filter/sort/page params |
| POST | `/api/v1/subscriptions` | Create |
| GET | `/api/v1/subscriptions/{id}` | Detail |
| PUT | `/api/v1/subscriptions/{id}` | Update |
| DELETE | `/api/v1/subscriptions/{id}` | Soft delete |
| POST | `/api/v1/subscriptions/{id}/decisions` | Record a decision |
| POST | `/api/v1/subscriptions/{id}/reviews` | Record a review |
| GET | `/api/v1/renewals?days=90` | Upcoming renewals and trial endings |
| GET | `/api/v1/dashboard/summary` | Dashboard figures in reporting currency |
| GET/PUT | `/api/v1/budgets` | Budgets per category |
| GET | `/api/v1/categories` | Category list |
| GET/PUT | `/api/v1/settings` | Settings |
| POST | `/api/v1/settings/test-notification` | Send a test push |
| GET | `/api/v1/fx-rates` | Current cached rates and their date |

### Data model (PostgreSQL)

| Table | Key columns |
|---|---|
| `category` | id, name, colour |
| `subscription` | id, name, vendor, category_id, type, billing_cycle, amount, price_after_trial, currency, start_date, next_renewal_date, anchor_day_of_month, notice_days, auto_renew, payment_label, owner, status, decision, usage_rating, last_reviewed_at, notes, deleted_at, created_at, updated_at, version |
| `decision_history` | id, subscription_id, decision, new_amount, reason, source (`UI`, `MCP`, `SYSTEM`), decided_at |
| `review` | id, subscription_id, usage_rating, keep, reviewed_at |
| `fx_rate` | base, quote, rate, rate_date (unique base+quote+rate_date) |
| `budget` | id, category_id, monthly_amount, currency |
| `notification_log` | id, subscription_id, renewal_date, offset_days, status, attempts, sent_at, **unique(subscription_id, renewal_date, offset_days)** |
| `settings` | single row: reporting_currency, time_zone, ntfy_server, ntfy_topic, ntfy_token (encrypted), reminder_offsets, review_interval_days |
| `payment` (Phase 3) | id, subscription_id, paid_on, amount, currency |
| `document` (Phase 3) | id, subscription_id, filename, content_type, size, storage_key |

- Schema changes only through **Flyway** migrations (`V1__init.sql`, …). Never edit an applied migration.
- Optimistic locking (`version`) on `subscription`.

### Scheduled jobs

| Job | Schedule (default) | Notes |
|---|---|---|
| FX rate refresh | Daily 06:00 | Frankfurter API; keep previous rates on failure |
| Renewal rollover | Daily 00:15 | F-04 rules; idempotent per date |
| Reminder sender | Daily 08:00 | F-05; exactly once via `notification_log` |
| Review prompts | Daily 08:05 | F-07 |

Jobs use `@Scheduled` with the configured time zone; each job is safe to run more than once.

---

## 6. Quality and engineering standards

### Test strategy (test pyramid)

| Level | Tools | Scope |
|---|---|---|
| Unit | JUnit 5, AssertJ, Mockito | Domain rules: cycle maths, month-end rollover, currency conversion, cancel-by, underused logic |
| Integration | Spring Boot Test, **Testcontainers (PostgreSQL)**, WireMock | Repositories, Flyway migrations, REST controllers, ntfy and FX clients (including 429/5xx retries) |
| Frontend | Vitest, React Testing Library, MSW | Components, forms and validation, currency formatting |
| End-to-end | **Cucumber (Gherkin) + Playwright for Java** | User journeys through the real UI against the full Docker Compose stack, with ntfy and FX stubbed by WireMock |

- **TDD is mandatory:** write a failing test first (red), make it pass with the simplest code (green), then refactor. Commits should show the test before or with the implementation.
- Coverage target: ≥ 85% line coverage on backend domain and service packages (JaCoCo); coverage is a guide, not a goal.
- Every acceptance criterion in this spec maps to at least one Cucumber scenario, tagged with the feature ID.

Example feature file (`e2e/src/test/resources/features/reminders.feature`):

```gherkin
@F-05
Feature: Renewal reminders
  So that I never pay for a subscription I meant to cancel
  I want a push notification before the cancel-by date

  Background:
    Given today is "2026-11-01"
    And ntfy notifications are configured

  Scenario: Reminder sent seven days before the cancel-by date
    Given a "Monthly" subscription "Claude Pro" costing 18.00 "GBP" renewing on "2026-11-08"
    When the reminder job runs
    Then 1 push notification is sent with title "Claude Pro renews in 7 days"

  Scenario: Reminder is not sent twice
    Given a "Monthly" subscription "Netflix" costing 10.99 "GBP" renewing on "2026-11-08"
    When the reminder job runs
    And the reminder job runs again
    Then 1 push notification is sent for "Netflix"

  Scenario: No reminder once I have decided to cancel
    Given a "Yearly" subscription "McAfee" costing 89.99 "GBP" renewing on "2026-11-08"
    And I have decided to "Cancel" "McAfee"
    When the reminder job runs
    Then no push notification is sent for "McAfee"
```

### Code formatting and static checks

- **Java:** [Spotify fmt-maven-plugin](https://github.com/spotify/fmt-maven-plugin) (google-java-format, Google style). Developers run `mvn com.spotify.fmt:fmt-maven-plugin:format`; the build runs the `check` goal in the `validate` phase, so unformatted code fails CI.
  ```xml
  <plugin>
    <groupId>com.spotify.fmt</groupId>
    <artifactId>fmt-maven-plugin</artifactId>
    <version>${fmt-maven-plugin.version}</version> <!-- pin the latest release from Maven Central -->
    <executions>
      <execution>
        <phase>validate</phase>
        <goals><goal>check</goal></goals>
      </execution>
    </executions>
  </plugin>
  ```
- **TypeScript:** ESLint + Prettier, `tsc --noEmit` in CI, `strict: true`.
- Dependency and secret scanning: Dependabot, GitHub secret scanning, OWASP dependency-check (Phase 2).

### Security and privacy

- No card numbers, bank details or passwords stored. Payment method is a free-text label.
- Secrets (ntfy token, DB password, MCP server API token) come from environment variables or a local `.env` that is git-ignored. `.env.example` documents them.
- The ntfy topic is random and private; the token is encrypted at rest.
- Seed and test data are fictional.

### Repository layout

```
subsense/
├── backend/              Spring Boot (Maven)
├── frontend/             React + TypeScript (Vite)
├── e2e/                  Cucumber + Playwright (Maven)
├── mcp-server/           SubSense MCP server (Spring AI), Phase 4
├── docs/
│   ├── adr/              Architecture Decision Records
│   └── features/         Per-feature plans written before implementation
├── .claude/commands/     Reusable AI workflow commands
├── .github/
│   ├── workflows/ci.yml
│   └── pull_request_template.md
├── docker-compose.yml    postgres, backend, frontend (+ wiremock for e2e)
├── CLAUDE.md             Instructions for the AI coding agent
├── SPEC.md               This document
└── README.md
```

### Git workflow and branch protection

- `main` is protected:
  - Changes only through pull requests; no direct pushes, no force pushes.
  - Required status checks: `backend` (fmt check, unit + integration tests), `frontend` (lint, type check, tests), `e2e` (Cucumber).
  - Branch must be up to date before merging; all review conversations resolved.
  - Squash merge only.
  - Note: GitHub does not let an author approve their own PR. For a solo repository, "required approvals" stays at 0 and each PR still gets a full self-review plus an AI review pass; raise it to 1 if collaborators join.
- Branch names: `feature/F-05-ntfy-reminders`, `fix/F-04-month-end-rollover`, `chore/…`.
- Commits follow **Conventional Commits** (`feat(reminders): send ntfy push 7 days before cancel-by`).

### CI (GitHub Actions)

On every PR and push to `main`:

1. **backend:** `mvn -B verify` (fmt check, unit and Testcontainers integration tests, JaCoCo report).
2. **frontend:** `npm ci`, `npm run lint`, `npm run typecheck`, `npm test`, `npm run build`.
3. **e2e:** start `docker compose` with WireMock, run `mvn -B -pl e2e verify`, upload the Cucumber HTML report and Playwright traces as artefacts.

---

## 7. AI-assisted development workflow

AI tooling (Claude Code) is used as **standard practice in every phase** to improve quality and delivery speed. The human developer owns every decision: AI drafts, the developer reviews, and nothing merges without passing CI and a human review of the PR.

| Stage | How AI is used | Human control point |
|---|---|---|
| **Design** | Claude Code (plan mode) reads `SPEC.md` and drafts a feature plan in `docs/features/F-xx.md`: API changes, schema changes, edge cases, test list. Significant choices are recorded as ADRs in `docs/adr/`. | Plan reviewed and approved before any code is written (`/plan-feature`) |
| **Acceptance tests** | AI turns the spec's acceptance criteria into Gherkin scenarios and proposes extra edge cases (leap years, month ends, currency rounding, time zones). | Developer prunes and approves scenarios |
| **Implementation** | AI implements in strict **TDD** cycles: failing test → minimal code → refactor, running the formatter and tests at each step (`/tdd`). | Developer reviews each cycle's diff |
| **Code review** | Before a PR is opened, AI reviews the diff against `CLAUDE.md` rules and the spec: correctness, missing tests, security, money and date handling (`/review-pr`). | Developer self-review of the PR; branch protection requires green CI |
| **Testing** | AI suggests missing test cases, generates test data builders, and analyses failing CI logs. | Developer confirms the tests prove the behaviour |
| **Debugging** | AI reads logs and stack traces, proposes a root cause and a failing test that reproduces it before the fix. | Developer verifies the root cause |
| **Documentation** | AI keeps the README, OpenAPI descriptions and ADRs in sync with the code. | Reviewed in the PR |

**Rules**

- AI never pushes to `main`, never merges, and never disables or weakens a test to make it pass.
- Every PR states how AI was used (see the PR template).
- `CLAUDE.md` is the single source of agent instructions and is updated whenever a convention changes.

---

## 8. Delivery phases

| Phase | Features | Definition of done |
|---|---|---|
| **0. Foundations** | Monorepo, Docker Compose, Flyway V1, CI pipeline, fmt plugin, ESLint/Prettier, CLAUDE.md, PR template, Cucumber skeleton with one passing scenario | `docker compose up` runs the stack; CI green on an empty feature |
| **1. Core register** | F-01, F-02, F-03 (cards + category donut), basic F-04 decisions | Can add, edit, list and delete subscriptions in GBP/INR/USD and see correct totals |
| **2. Decisions and alerts** | F-04 rollover, F-05 ntfy reminders, F-06 budgets, F-07 reviews and savings | Phone receives reminders exactly once; rollover correct across month ends |
| **3. Insights** | F-08, F-09, F-10, F-12 | As per each feature's acceptance criteria |
| **4a. Talk to my data (read-only)** | F-11 MCP server with read-only tools, resource and prompt | Claude Desktop answers renewal, spend, budget and underused questions from live data |
| **4b. Act from chat** | F-11 write tools with approval and audit trail | A renew/cancel decision made in chat appears in the decision history with source `MCP` |

Each feature is delivered as one or more small PRs, each with its plan, tests and passing CI.

---

## 9. Open questions

- Hosting beyond localhost (e.g. a small VPS or Fly.io) once F-12 authentication exists.
- Whether to support email reminders alongside ntfy in a later phase.
