---
description: Draft an implementation plan for a SubSense feature from SPEC.md, then stop for approval
argument-hint: <feature-id, e.g. F-05>
---

Draft an implementation plan for feature $ARGUMENTS.

1. Read `CLAUDE.md` and the $ARGUMENTS section of `SPEC.md`, plus any related existing code.
2. Write the plan to `docs/features/$ARGUMENTS-<short-name>.md` with these sections:
   - **Scope:** what is in and out of this change.
   - **API changes:** endpoints, request/response shapes, error cases.
   - **Schema changes:** new Flyway migration(s).
   - **Domain rules and edge cases:** list every one (month ends, leap years, currency rounding, time zones, retries, idempotency where relevant).
   - **Test plan:** the unit, integration and Cucumber tests you will write, in the order you will write them.
   - **UI changes:** pages and components affected.
   - **Design:** main classes and interfaces, and how the design follows SOLID, DRY, KISS and YAGNI. Note any trade-off between them.
   - **New dependencies:** with justification, or "none".
   - **Risks and open questions.**
3. Do not write any production or test code.
4. Show me the plan and stop. Wait for my approval or changes.
