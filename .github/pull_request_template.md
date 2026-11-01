## Summary

<!-- What does this PR change, and why? -->

Feature: F-xx <!-- e.g. F-05 Renewal reminders -->
Plan: docs/features/F-xx-<name>.md

## Changes

-

## How it was tested

- [ ] Unit tests
- [ ] Integration tests (Testcontainers / WireMock)
- [ ] Cucumber scenarios: `@F-xx`
- [ ] Manually checked in the UI (screenshots below if UI changed)

## AI assistance

<!-- How was AI tooling used in this PR? Tick all that apply and add a line of detail. -->

- [ ] Plan drafted with AI, reviewed and edited by me
- [ ] Acceptance scenarios / edge cases suggested by AI
- [ ] Implementation via AI-driven TDD cycles, each diff reviewed
- [ ] AI pre-review of this diff (`/review-pr`); findings addressed below
- [ ] Debugging / root-cause analysis with AI
- [ ] No AI used

AI review findings and what I did about them:

-

## Checklist

- [ ] Java formatted (`fmt-maven-plugin`) and `mvn verify` passes
- [ ] Frontend lint, typecheck and tests pass
- [ ] Design follows SOLID, DRY, KISS and YAGNI
- [ ] Money uses `BigDecimal` / `NUMERIC`, never `double`
- [ ] Dates use the injected `Clock`
- [ ] New schema changes are new Flyway migrations
- [ ] No secrets or real personal data
- [ ] README / OpenAPI / ADRs updated if needed

## Screenshots

<!-- UI changes only -->
