---
description: AI pre-review of the current branch's changes against CLAUDE.md and SPEC.md before opening a PR
---

Review the changes on the current branch against `main` (`git diff main...HEAD`).

Check, and report findings grouped by severity (must fix / should fix / nit), each with file and line:

1. **Spec:** does the behaviour match the relevant `SPEC.md` feature and its acceptance criteria? Is anything missing?
2. **Tests:** is every behaviour covered? Any acceptance criterion without a Cucumber scenario? Any test that does not actually prove the behaviour?
3. **Correctness:** money as `BigDecimal` with currency; dates through the injected `Clock`; month-end and leap-year handling; time zones; null handling.
4. **Reliability:** timeouts, retries on 429/5xx, idempotency of jobs and notifications.
5. **Security and privacy:** no secrets, no card or bank data, no real personal data, input validation, safe error messages.
6. **Database:** changes only through new Flyway migrations; indexes for new queries.
7. **Frontend:** types matching the OpenAPI spec, Zod validation, accessible labels, currency formatting per locale.
8. **Design principles:** SOLID (single responsibility, extension without growing switch statements, small interfaces, constructor injection, no framework or clock dependencies in domain code), DRY (one source of truth for each rule), KISS (simplest design that passes the tests), YAGNI (nothing beyond the current feature).
9. **Conventions:** formatter run, naming, Conventional Commit messages, docs/OpenAPI/ADRs updated.

Do not change any code. End with a short list I can paste into the PR template's "AI review findings" section.
