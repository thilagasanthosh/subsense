# CLAUDE.md

Instructions for AI coding agents working in this repository. Read this file and `SPEC.md` before starting any task.

## Project

**SubSense** is a personal subscription spend manager: a register of subscriptions in GBP, INR and USD, renewal decisions, budgets, usage reviews and push reminders via ntfy. There are two ways into the same data: the React web UI for adding, editing and reviewing, and an MCP server that lets AI clients (such as Claude Desktop) answer questions about it in plain language. Both go through the same REST API.

- `backend/`: Java 21, Spring Boot 3, Spring Data JPA, Flyway, PostgreSQL 16
- `frontend/`: React, TypeScript (strict), Vite, TanStack Query, React Hook Form + Zod, Recharts
- `e2e/`: Cucumber (Gherkin) + Playwright for Java, run against the Docker Compose stack
- `mcp-server/` (Phase 4): Spring AI MCP server. A thin adapter over the REST API: no database access and no duplicated business rules. Tools are read-only unless write tools are explicitly enabled
- `SPEC.md` is the source of truth for behaviour. Feature IDs (F-01 … F-12) are used everywhere.

## Commands

```bash
# Whole stack
docker compose up -d

# Backend (from the repo root)
mvn -pl backend com.spotify.fmt:fmt-maven-plugin:format   # format Java (run before every commit)
mvn -pl backend test                                      # unit tests
mvn -pl backend verify                                    # fmt check + unit + Testcontainers ITs

# Frontend
cd frontend
npm run format && npm run lint && npm run typecheck && npm test
npm run dev:mock                               # UI with sample data, no backend

# End-to-end (fixed exchange rates from the WireMock stub)
SUBSENSE_FX_BASE_URL=http://fx-stub:8080 docker compose --profile e2e up -d --build --wait
mvn -pl e2e verify                            # all Cucumber scenarios
mvn -pl e2e verify -Dcucumber.filter.tags="@F-05"
```

## How to work: plan, then TDD

1. **Plan first.** For any feature or non-trivial change, write a plan in `docs/features/F-xx-<name>.md`: scope, API and schema changes, edge cases, and the list of tests you will write. **Stop and wait for approval** before writing code.
2. **Acceptance tests.** Turn the feature's acceptance criteria from `SPEC.md` into Cucumber scenarios tagged `@F-xx`. They should fail until the feature exists.
3. **TDD, strictly.** For every behaviour:
   - **Red:** write one failing test. Run it and confirm it fails for the right reason.
   - **Green:** write the simplest code that makes it pass.
   - **Refactor:** clean up with all tests green.
   - Repeat. Do not write production code without a failing test that needs it.
4. **Verify.** Run the formatter, then `mvn verify` / frontend checks / relevant e2e tags. All must pass.
5. **Self-review** the full diff against the rules below before saying the task is done.

## Design principles

Apply these in every plan, implementation and review. When principles pull in different directions, prefer the simpler option (KISS, YAGNI) and explain the trade-off in the plan.

### SOLID

- **Single Responsibility:** each class has one reason to change. Controllers handle HTTP only, services hold business rules, repositories handle persistence, clients handle outbound HTTP. Split a class when it starts mixing these.
- **Open/Closed:** extend behaviour by adding code, not by editing growing `if`/`switch` chains. Example: each `BillingCycle` knows how to advance a date and compute its monthly equivalent, so adding a cycle doesn't touch the rollover job.
- **Liskov Substitution:** implementations of an interface must honour its contract. A `NotificationSender` must behave the same whether it is the ntfy client or a test fake (same exceptions, same idempotency expectations).
- **Interface Segregation:** keep interfaces small and focused (`FxRateProvider`, `NotificationSender`, `ReminderPolicy`) rather than one large "service" interface.
- **Dependency Inversion:** domain and service code depend on interfaces, not on HTTP clients, frameworks or `LocalDate.now()`. Inject dependencies through constructors (no field injection).

### DRY (Don't Repeat Yourself)

- One source of truth for each rule: cycle maths lives in `BillingCycle`, money conversion in the `currency` package, validation rules in one place per layer.
- API types live once in `src/api/types.ts`, matching the OpenAPI spec; form rules live once in `src/lib/subscriptionForm.ts`.
- Share test data through builders, not copy-pasted setup.
- Don't over-apply DRY: two pieces of code that look alike but change for different reasons should stay separate.

### KISS (Keep It Simple)

- Choose the simplest design that meets the spec and passes the tests. Plain classes and methods before patterns, frameworks or abstractions.
- No clever code: readable names, short methods, early returns, no deep nesting.
- Prefer standard Spring Boot and library features over custom infrastructure.

### YAGNI (You Aren't Gonna Need It)

- Build only what the current feature in `SPEC.md` needs. No speculative interfaces, configuration options, generic frameworks or "future-proofing".
- Don't implement later-phase features early, even partially.
- An abstraction needs at least two real uses, or a testing need, before it is introduced.

During `/review-pr`, flag violations of these principles alongside correctness issues.

## Rules

### Must

- Money: `BigDecimal` in Java, `NUMERIC(12,2)` in Postgres, `HALF_EVEN` rounding only at display/total time. Always store the currency with the amount.
- Dates: use the injected `java.time.Clock`; never call `LocalDate.now()` without it. Respect the configured time zone (default Europe/London).
- Billing-cycle maths must handle month ends and leap years (anchor day of month).
- Schema changes only via new Flyway migrations. Never edit an applied migration.
- Outbound HTTP (ntfy, FX): timeouts, retry with backoff on 429/5xx, and log the outcome. Reminders must be idempotent through the `notification_log` unique key.
- Errors returned as RFC 7807 `ProblemDetail` with field-level messages.
- Frontend: keep `src/api/types.ts` in step with the OpenAPI spec (`/v3/api-docs`); validate forms with Zod; keep `src/mocks` in step with API changes; format money with `Intl.NumberFormat` (`en-GB`, `en-IN`, `en-US`).
- Keep `README.md`, OpenAPI annotations and ADRs (`docs/adr/`) in sync with changes.

### Must not

- Never use `double`/`float` for money.
- Never store card numbers, bank details or passwords. The payment method is a label only.
- Never commit secrets or real personal data. Use `.env` (git-ignored) and fictional seed data.
- Never delete, skip or weaken a test to make the build pass. If a test is wrong, explain why and ask.
- Never push to `main`, merge PRs, or change CI and branch protection without being asked.
- Do not add new dependencies without stating why in the plan.

## Testing conventions

- Unit tests: `*Test.java`, JUnit 5 + AssertJ; one behaviour per test; names describe behaviour (`advancesMonthlyRenewalFrom31JanTo28Feb`).
- Integration tests: `*IT.java`, Testcontainers PostgreSQL, WireMock for ntfy and Frankfurter.
- Use test data builders (`aSubscription().monthly().costing("18.00", GBP).build()`).
- Frontend: Vitest + React Testing Library + MSW; test what the user sees, not implementation details.
- Every acceptance criterion in `SPEC.md` has at least one Cucumber scenario tagged with its feature ID.

## Git conventions

- Branches: `feature/F-05-ntfy-reminders`, `fix/F-04-month-end-rollover`, `chore/<topic>`.
- Conventional Commits: `feat(reminders): …`, `fix(currency): …`, `test(rollover): …`, `docs: …`.
- Small PRs. Fill in `.github/pull_request_template.md`, including how AI was used.

## Definition of done

- [ ] Plan approved and saved in `docs/features/`
- [ ] Acceptance criteria covered by Cucumber scenarios
- [ ] Tests written first; unit, integration and e2e passing
- [ ] `mvn verify` passes (includes fmt check); frontend lint, typecheck and tests pass
- [ ] Design follows SOLID, DRY, KISS and YAGNI; any trade-off explained in the plan or PR
- [ ] No secrets, no real personal data, no money as `double`
- [ ] Docs and OpenAPI updated
