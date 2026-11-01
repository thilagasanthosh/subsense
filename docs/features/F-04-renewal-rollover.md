# Plan: F-04 renewal rollover (Phase 2)

Status: Approved (2026-11-01) · Implemented · open questions 1–3 resolved as suggested: pending with auto-renew off lapses to `CANCELLED`; Cucumber uses the test-only rollover trigger; Cancel takes effect on the renewal date, renewals advance the day after

Branch: `feature/F-04-renewal-rollover`

## Scope

- **In:**
  - A daily rollover job (00:15, configured time zone) that applies the F-04 rules to every subscription whose renewal date has arrived.
  - Month-end handling through an anchor day of month (31 Jan → 28/29 Feb → 31 Mar).
  - A stored `CANCELLED` state.
  - Free trials turning into paid plans.
  - Downgrades applied at renewal.
  - An audit entry with source `SYSTEM` for every change the job makes.
  - Catch-up when the app was not running at 00:15.
- **Already done in Phase 1 (unchanged):** recording Renew / Cancel / Downgrade decisions, `CANCELLING` status, decision history.
- **Out:**
  - Reminders, including the rule that "no further reminders are sent" (F-05). This plan only makes sure a `CANCELLED` subscription is clearly marked, so F-05 can skip it.
  - Reactivating a cancelled subscription.
  - Budgets and reviews (F-06, F-07).
  - Settings page and the 12-month projection chart.

## API changes

- **No new public endpoints.**
- `status` in `SubscriptionView` can now be `CANCELLED`. The enum value already exists in the OpenAPI schema and `src/api/types.ts`.
- `POST /api/v1/subscriptions/{id}/decisions` on a `CANCELLED` subscription returns **409 Conflict** as a `ProblemDetail`: "Subscription is cancelled".
- `PUT /api/v1/subscriptions/{id}` on a `CANCELLED` subscription also returns **409**. It is kept for reports, not edited.
- **Test-only trigger (see open question 2):** `POST /api/v1/jobs/rollover` runs the job now using the injected `Clock`, and returns `{ "processed": n }`. It only exists when `subsense.jobs.manual-trigger=true`, which is set only in the e2e Compose profile. It is never on by default.

## Schema changes

New migration `V3__renewal_rollover.sql` (V1 and V2 are not edited):

```sql
ALTER TABLE subscription ADD COLUMN renewal_anchor_day SMALLINT;
UPDATE subscription SET renewal_anchor_day = EXTRACT(DAY FROM next_renewal_date);
ALTER TABLE subscription
    ALTER COLUMN renewal_anchor_day SET NOT NULL,
    ADD CONSTRAINT renewal_anchor_day_range CHECK (renewal_anchor_day BETWEEN 1 AND 31);

ALTER TABLE subscription ADD COLUMN cancelled_on DATE;
```

- `renewal_anchor_day`: the day of month the user chose. Without it, 31 Jan → 28 Feb would drift to 28 Mar.
- `cancelled_on`: set when a Cancel takes effect. `status()` returns `CANCELLED` when it is set. This is one nullable date, rather than storing the whole status, which stays derived.

## Domain rules and edge cases

**When the job acts**

1. "Today" is `LocalDate.now(clock)` in the configured zone (default Europe/London), never the server's zone.
2. A renewal on day D is **advanced once D has passed** (`nextRenewalDate < today`), so "renews today" stays visible all day and F-05 can send a same-day reminder.
3. A **Cancel takes effect on the renewal date** (`nextRenewalDate <= today`), as SPEC.md says.
4. Soft-deleted subscriptions and subscriptions that are already `CANCELLED` are ignored.

**Rules per decision** (applied once per passed renewal)

| Decision / state | Result |
|---|---|
| `RENEW` | Advance one cycle, then reset the decision to `PENDING` |
| `PENDING` and auto-renew on | Advance one cycle, decision stays `PENDING` |
| `PENDING` and auto-renew off | Lapses: `cancelled_on = renewal date`, status `CANCELLED` (**open question 1**) |
| `CANCEL` | `cancelled_on = renewal date`, status `CANCELLED`, the date is not advanced |
| `DOWNGRADE` | `amount = downgrade_amount`, `downgrade_amount` cleared, advance, reset to `PENDING` |
| Free trial, not cancelled | `type = PAID`, `amount = price_after_trial`, `price_after_trial` cleared, status `ACTIVE`, advance one cycle |
| Free trial with `DOWNGRADE` | Becomes `PAID` at the downgrade amount (the user's later choice wins over the listed price) |
| Free trial with `CANCEL` | `CANCELLED` (never charged) |

**Month ends and leap years** (all in `BillingCycle`)

5. Next date = `renewal + cycle months`, then the day is set to `min(anchorDay, lengthOfMonth)`.
   - Monthly from 31 Jan: 28 Feb 2027 → 31 Mar → 30 Apr → 31 May.
   - In a leap year: 31 Jan 2028 → **29 Feb 2028**.
   - Quarterly from 30 Nov: 28 Feb → 30 May → 30 Aug → 30 Nov.
   - Yearly from 29 Feb 2028: 28 Feb 2029 → … → **29 Feb 2032**.
6. The anchor is set from `nextRenewalDate` on create, and on update **only when the user changes the renewal date**. Otherwise, editing the name of a subscription currently on 28 Feb would reset its anchor from 31 to 28.

**Catch-up and idempotency**

7. If the app was off for several cycles, the job loops until `nextRenewalDate >= today`. The first passed renewal uses the stored decision; later ones are `PENDING` (with auto-renew), so they keep advancing. The loop has a safety limit of 1,000 cycles.
8. The job also runs once on startup, like the FX refresh, because a laptop app is often asleep at 00:15. A setting `subsense.rollover.run-on-startup` defaults to true.
9. **Idempotent:** after a run, nothing has `nextRenewalDate < today`, so running again the same day changes nothing and writes no history.
10. Each subscription is processed in its own transaction. If an edit collides with the job (an optimistic lock failure on `version`), that subscription is logged and skipped, then picked up on the next run. One bad row never blocks the others.

**Money**

11. Downgrade and trial amounts are copied as `BigDecimal` and never rounded. The currency is unchanged, because a renewal never changes currency.

**Audit**

12. Every change writes a `decision_history` row with source `SYSTEM` and a reason. Examples:
    - "Renewed 5 Nov 2026; next renewal 5 Dec 2026"
    - "Cancelled on 6 Nov 2026"
    - "Trial ended; now £29.99 monthly"
    - "Downgraded to ₹199.00"

    The UI history list already shows the source.

**Effects elsewhere**

13. Dashboard spend, active count, "renewing in 30 days" and "Needs a decision" exclude `CANCELLED` (as they already exclude `CANCELLING`).
14. The Subscriptions list still shows `CANCELLED` rows with a grey "Cancelled" badge, kept for reports. The Renewals timeline hides them.

## Test plan (in the order written)

**Unit: `BillingCycleTest`** (date maths first, as everything depends on it)

1. `advancesMonthlyRenewalBySameDayOfMonth` (15 Nov → 15 Dec)
2. `advancesMonthlyRenewalFrom31JanTo28Feb`
3. `advancesMonthlyRenewalFrom31JanTo29FebInLeapYear`
4. `returnsToAnchorDay31InMarchAfterFebruary`
5. `advancesQuarterlyRenewalFrom30NovTo28FebThen30May`
6. `advancesYearlyRenewalFrom29FebTo28FebInNonLeapYear`
7. `returnsYearlyRenewalTo29FebInNextLeapYear`
8. `advancesDecemberRenewalIntoNextYear`

**Unit: `SubscriptionTest`** (entity behaviour, with builders)

9. `setsAnchorDayFromRenewalDateOnCreate`
10. `keepsAnchorDayWhenUpdateLeavesRenewalDateUnchanged`
11. `resetsAnchorDayWhenUserChangesRenewalDate`
12. `renewDecisionAdvancesDateAndResetsToPending`
13. `pendingWithAutoRenewAdvancesDate`
14. `pendingWithoutAutoRenewLapsesToCancelled`
15. `cancelDecisionBecomesCancelledOnRenewalDate`
16. `downgradeAppliesNewAmountAndClearsDowngrade`
17. `freeTrialBecomesPaidAtPriceAfterTrial`
18. `freeTrialWithDowngradeBecomesPaidAtDowngradeAmount`
19. `cancelledFreeTrialIsNeverCharged`
20. `statusIsCancelledOnceCancelledOnIsSet`

**Unit: `RenewalRolloverServiceTest`** (fixed `Clock`, in-memory fakes)

21. `leavesRenewalDueTodayUntouched`
22. `advancesRenewalThatPassedYesterday`
23. `catchesUpSeveralMissedMonthlyRenewals`
24. `secondRunOnSameDayChangesNothing`
25. `recordsSystemHistoryEntryForEachChange`
26. `ignoresDeletedAndCancelledSubscriptions`
27. `usesLondonDateNotUtcJustAfterMidnight` (23:30 UTC on 31 Mar = 00:30 BST on 1 Apr)
28. `carriesOnWhenOneSubscriptionFailsToSave`

**Unit: `SubscriptionServiceTest`, `DashboardServiceTest`**

29. `rejectsDecisionOnCancelledSubscription` (409)
30. `rejectsEditOfCancelledSubscription` (409)
31. `excludesCancelledFromSpendAndCounts`

**Integration (Testcontainers PostgreSQL, fixed `Clock`): `RenewalRolloverIT`**

32. V3 migration backfills `renewal_anchor_day` from existing rows.
33. **AC 1:** monthly 31 Jan with RENEW → run on 1 Feb → 28 Feb → run on 1 Mar → 31 Mar. Repeat for 2028 → 29 Feb.
34. **AC 2:** CANCEL → run on the renewal date → `GET` returns status `CANCELLED`, the decision endpoint returns 409.
35. A job run at the same time as a UI edit: the edit wins and the job retries next run.

**End-to-end (Cucumber `@F-04`, added to `subscriptions.feature` or a new `rollover.feature`)**

36. `Scenario: A renewal that has passed moves to the next billing date`. Create a monthly subscription renewing yesterday through the API, trigger the rollover, and the list shows it renewing in about a month.
37. `Scenario: A cancelled subscription is shown as Cancelled after its renewal date`. Cancel a subscription renewing today, trigger the rollover, and the list shows "Cancelled" while the dashboard spend excludes it.
38. `Scenario: A free trial that has ended becomes a paid plan`.

The exact 31 Jan / 29 Feb dates are proved in tests 2–7 and 33. Cucumber runs against the real clock in Docker (see open question 2).

**Frontend (Vitest + MSW)**

39. `SubscriptionsPage` shows a "Cancelled" badge.
40. `RenewalsPage` hides cancelled subscriptions.
41. `SubscriptionDetailPage` hides the Renew / Cancel / Downgrade controls for a cancelled subscription and shows "Cancelled on <date>".

## UI changes

- `Badges.tsx`: grey style for `CANCELLED`. The `format.ts` label already exists.
- `SubscriptionDetailPage`: for cancelled subscriptions, no decision controls or Edit button; shows "Cancelled on 6 Nov 2026"; history shows `SYSTEM` entries.
- `RenewalsPage`: filter out `CANCELLED` as well as `CANCELLING`.
- `src/api/types.ts`: add `cancelledOn: string | null` to `Subscription`.
- `src/mocks/db.ts` and handlers: same status rules, so `npm run dev:mock` stays in step.

## Design

| Class | Responsibility |
|---|---|
| `BillingCycle` (existing enum) | Add `LocalDate next(LocalDate from, int anchorDay)`. Cycle maths stays in one place (DRY); a new cycle adds an enum value, not a branch in the job (open/closed). |
| `Subscription` (entity) | Add `rollOver(LocalDate today)`, which returns a small `RolloverOutcome` (what changed, for the audit reason). The rules live with the data they change. |
| `RenewalRolloverService` | Finds due subscriptions, calls `rollOver` for each in its own transaction, saves history, logs a summary ("rolled over 3, cancelled 1, skipped 0"). Depends on the repositories and `Clock` only. |
| `RenewalRolloverJob` | `@Scheduled(cron = "${subsense.rollover.cron}", zone = "${subsense.time-zone}")` plus a run on startup. Only triggers the service (single responsibility, same shape as `FxRateRefreshJob`). |
| `RolloverJobController` | Test-only trigger, `@ConditionalOnProperty("subsense.jobs.manual-trigger")`. |
| `SubscriptionRepository` | Add `findDueForRollover(LocalDate today)`: not deleted, not cancelled, `next_renewal_date <= today`. Uses the existing partial index. |

- **SOLID:** each class above has one reason to change. Nothing calls `LocalDate.now()` without the clock. No new interface is added, because there's only one rollover policy (no second use yet).
- **KISS:** plain loops and early returns in `rollOver`. A `switch` on `Decision` (4 fixed values from SPEC.md) is clearer than a strategy class per decision.
- **YAGNI:** no "reactivate", no configurable rules, no generic job framework.
- **Trade-off:** the decision `switch` bends open/closed a little. Decisions are a closed list in the spec, so the simpler code wins. If a fifth decision ever appears, it becomes a method on `Decision`.

New configuration in `application.yml`:

```yaml
subsense:
  rollover:
    cron: ${SUBSENSE_ROLLOVER_CRON:0 15 0 * * *}
    run-on-startup: ${SUBSENSE_ROLLOVER_ON_STARTUP:true}
  jobs:
    manual-trigger: ${SUBSENSE_JOBS_MANUAL_TRIGGER:false}
```

README (Configuration table) and a new ADR `0003-renewal-anchor-day.md` are updated alongside.

## New dependencies

None.

## Risks and open questions

1. **Pending with auto-renew off:** SPEC.md doesn't say what happens. I suggest **it lapses to `CANCELLED`**, because not auto-renewing means the service ends unless you act. The alternative is to leave it "overdue" and keep asking. Which do you want?
2. **Cucumber and the clock:** e2e runs against Docker with the real date, so Cucumber can't reach 31 Jan. I suggest the test-only `POST /api/v1/jobs/rollover` (off by default, on only in the e2e profile), with the month-end dates proved in unit and IT tests. The alternative is a "fixed date" setting for the e2e backend: more realistic, but it needs a restart for each date. OK to go with the trigger endpoint?
3. **Cancel timing:** I've followed SPEC.md (Cancelled on the renewal date) while renewals advance the day after. Happy to make both "the day after" if you'd prefer one rule.
4. **Your own data:** after this ships, your two Airtel plans (Renew, 5 and 6 Nov) will move to 5 and 6 Dec on 6 and 7 Nov, and their decisions reset to Pending. That's expected behaviour, not data loss.
5. **Risk, time zones:** a job at 00:15 London time during the clock change. Covered by test 27, and Spring's `zone` attribute on `@Scheduled`.

## Implementation notes (deviations from this plan)

- **Tests 5–8, 13, 19, 24, 26, 27** passed on first run, because earlier code already covered them. They are kept as edge-case guards.
- **Test 20** is covered by tests 14, 15 and 19, which assert the `CANCELLED` status. It was not written separately.
- **Test 35** (job vs UI edit at the same moment) is deferred, because it can't be made deterministic in an IT. Each subscription runs in its own transaction, and a failure is logged and skipped (unit test 28). An optimistic-lock failure takes that same path.
- **No 1,000-cycle safety limit:** every pass of the catch-up loop either moves the date forward or sets `cancelled_on`, so the loop always ends (YAGNI).
- **Unit tests use Mockito**, matching `SubscriptionServiceTest`, rather than hand-written fakes. `TransactionOperations.withoutTransaction()` stands in for real transactions.
- **Audit reasons** use ISO dates and plain amounts ("Downgraded; now 199.00 INR. Renewed on 2026-11-06; next renewal 2026-12-06"). The UI formats money elsewhere, and the backend does no locale formatting.
- **`renewal_anchor_day` is `INTEGER`**, not `SMALLINT`, to match the entity's `int`. Schema validation caught this before V3 was applied anywhere.
- **Badges:** `CANCELLED` already used the neutral (grey) badge and the "Cancelled" label, so `Badges.tsx` needed no change.
- **Added:** `ConflictException` (409 problem detail) and `cancelledOn` in `SubscriptionView` and `src/api/types.ts`.
- **CI:** with your approval, the e2e job now sets `SUBSENSE_JOBS_MANUAL_TRIGGER: 'true'` next to `SUBSENSE_FX_BASE_URL`.
- **Found while testing (pre-existing, fixed separately):** `mvn -pl e2e verify` reported `Tests run: 0` and BUILD SUCCESS even when a scenario failed, because of maven-surefire-plugin 3.5.3. `e2e/pom.xml` now uses 3.5.4, which counts each scenario and fails the build on a failing one.
