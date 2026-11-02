# Plan: F-05 renewal reminders via ntfy (Phase 2)

Status: Approved (2026-11-01) · open questions 1–4 resolved as suggested: settings read-only from environment variables; catch-up sends the latest missed reminder; at most once after a crash; CI e2e env change approved

Branch: `feature/F-05-ntfy-reminders` (from `main` at `c3af213`, which includes F-04)

## Scope

- **In:**
  - A daily reminder job (08:00, configured time zone, and once on startup) that sends ntfy push notifications.
  - Reminders 14, 7 and 1 day(s) before a subscription's **cancel-by date** (or its trial end), plus a **"renews today"** confirmation.
  - **Exactly once:** each reminder is recorded in `notification_log` with a unique key.
  - The ntfy client, with timeouts and retry with backoff on 429/5xx.
  - A **"Send test notification"** endpoint and a small **Settings** page that shows the ntfy setup and has the button.
- **Out:**
  - Editable settings stored in the database: the `settings` table, an encrypted token, and editing offsets in the UI (see open question 1).
  - Budget alerts (F-06) and review prompts (F-07).
  - Email reminders.
  - Reminders for subscriptions that are `CANCELLING` or `CANCELLED`.

## API changes

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/settings/notifications` | `{ "server": "https://ntfy.sh", "topicConfigured": true, "offsets": [14, 7, 1] }`. The topic and token are never returned. |
| POST | `/api/v1/settings/test-notification` | Sends "SubSense test notification". **204** when ntfy accepts it. **503** problem detail "Notifications are not configured" when no topic is set. **502** problem detail "ntfy did not accept the notification" after retries fail. |
| POST | `/api/v1/jobs/reminders` | **Test only**, like `/jobs/rollover`. Exists only when `subsense.jobs.manual-trigger=true`. Returns `{ "sent": n, "failed": n, "skipped": n }`. |

`RolloverJobController` becomes `JobController`, which hosts both test-only triggers (one class for the test-only endpoints).

## Schema changes

New migration `V4__notification_log.sql`:

```sql
CREATE TABLE notification_log (
    id               BIGSERIAL PRIMARY KEY,
    subscription_id  BIGINT       NOT NULL REFERENCES subscription (id),
    renewal_date     DATE         NOT NULL,
    offset_days      INTEGER      NOT NULL CHECK (offset_days BETWEEN 0 AND 365),
    status           VARCHAR(10)  NOT NULL,   -- SENDING, SENT, FAILED
    attempts         INTEGER      NOT NULL DEFAULT 0,
    last_error       VARCHAR(500),
    sent_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_notification_log UNIQUE (subscription_id, renewal_date, offset_days)
);
```

The unique constraint is also the index used for "has this reminder been sent?" lookups. No other indexes are needed.

## Domain rules and edge cases

**Which reminder is due** (`ReminderPolicy`, a pure function of the subscription, today and the offsets)

1. "Today" is `LocalDate.now(clock)` in the configured zone (Europe/London), so 08:00 London is the same day in BST and GMT.
2. **Countdown reminders** go only to status `TRIAL` or `ACTIVE` with decision `PENDING`. `daysLeft = cancelByDate − today`.
3. The **due offset** is the smallest configured offset that is ≥ `daysLeft`, when `daysLeft ≥ 1`. With offsets 14/7/1:
   - 7 days left → offset 7 is due.
   - **6 days left → offset 7 is due (catch-up).** If the app was off on the 7-day morning, the reminder still goes out late, saying "in 6 days". It goes out once, because offset 7 is then logged.
   - More than 14 days left → nothing is due.
   - See open question 2 for the catch-up rule.
4. **"Renews today"** (offset 0, from the renewal date) is sent on the renewal date when status is `TRIAL`/`ACTIVE` and decision is `PENDING`, `RENEW` or `DOWNGRADE`. It's the confirmation the spec allows after a decision. Decision `CANCEL` (status `CANCELLING`) gets **no reminders at all** (AC 4). F-04 makes those `CANCELLED` on that day anyway.
5. Offsets come from configuration (`SUBSENSE_REMINDER_OFFSETS=14,7,1`). They're sorted, duplicates are removed, values must be 1–365, and invalid config stops the app from starting with a clear message.
6. Notice periods: with notice, the countdown is to the cancel-by date, not the renewal date. That's the date that matters.
7. A month-end renewal or a leap year doesn't matter here. The dates come from F-04's `nextRenewalDate` and `cancelByDate()`.

**Message** (`ReminderMessage`)

8. **Title:**
   - "Claude Pro renews in 7 days", or "… renews tomorrow"
   - "Claude Pro: 3 days left to cancel" when there's a notice period
   - "LinkedIn Premium trial ends in 7 days"
   - "Claude Pro renews today"
9. **Body:** the amount in the subscription's own currency, formatted per locale (£ `en-GB`, ₹ `en-IN` with Indian grouping, $ `en-US`, rounded `HALF_EVEN` only here), then the cycle, the cancel-by date ("8 Nov 2026"), and the decision. Example: "£18.00 monthly · cancel by 8 Nov 2026 · not decided yet". Trials show the price after the trial.
10. **Click:** `SUBSENSE_APP_URL/subscriptions/{id}` (default `http://localhost:3000`).
11. **Priority:** ntfy `high` (4) for 1-day and "today" reminders, `default` (3) otherwise. **Tags:** `bell`.
12. No card data, payment labels or notes in messages (privacy). The topic and token are never logged.

**Exactly once and reliability**

13. **Claim before sending.** In its own transaction, the job inserts a `notification_log` row with status `SENDING`. If the unique key already exists:
    - status `SENT` or `SENDING`: skip.
    - status `FAILED`: switch it back to `SENDING` and send again.
    Two job runs at the same moment (startup plus schedule) can't both send, because the database's unique key decides.
14. After ntfy answers 2xx, the row is marked `SENT` with `sent_at` and the attempt count. Otherwise it's marked `FAILED` with `last_error` (truncated, no secrets) and retried on the next run (AC 3, and the spec's "retried on the next run").
15. **Crash between ntfy accepting and `SENT` being saved:** the row stays `SENDING` and is **not** sent again. That means at most once, and "never sent twice" wins over "always delivered". The job logs a warning for rows stuck in `SENDING` for more than an hour (see open question 3).
16. **Retries inside one send:** up to 3 attempts on HTTP 429, 5xx, or an I/O error/timeout, with exponential backoff (1 s, then 2 s; configurable, 0 in tests). If ntfy sends a `Retry-After` header on 429, it's honoured, capped at 10 s. Any other 4xx (for example 401 for a wrong token) fails at once without retry.
17. **Timeouts:** connect 5 s and read 10 s, reusing the existing `spring.http.client.*` settings.
18. Each subscription is processed separately. One failure never stops the rest, and the run ends with a summary log line ("sent 2, failed 0, skipped 5").
19. **Not configured** (no topic): the job logs "Reminders disabled: SUBSENSE_NTFY_TOPIC not set" once per run and sends nothing. The test endpoint returns 503.
20. **Deleted or `CANCELLED` subscriptions** are never loaded, using the existing "live subscriptions" query plus a status filter.

## Test plan (in the order written)

**End-to-end first (Cucumber `@F-05`, new `reminders.feature`, expected to fail):**

1. `Scenario: Reminder sent seven days before the cancel-by date`. "Claude Pro" renews in 7 days with no notice, the reminder job runs, and exactly 1 push notification is sent with the title "Claude Pro renews in 7 days". (AC 1)
2. `Scenario: Reminder is not sent twice`. The job runs twice and 1 push is sent for "Netflix". (AC 2)
3. `Scenario: A busy ntfy server is retried`. The stub answers 503 for "Spotify" first, then 200. The push for "Spotify" is accepted once after 2 attempts, and the job reports 1 sent. (AC 3)
4. `Scenario: No reminder once I have decided to cancel`. Cancel "McAfee" from the dashboard, the job runs, and no push is sent for "McAfee". (AC 4)

The steps use relative dates ("renewing in 7 days"), because Docker runs on the real date. The ntfy stub is the existing WireMock container with new mappings. Steps reset and count its requests through WireMock's admin API.

**Unit tests**

`MoneyFormatTest` (currency package: one place for display formatting):
5. `formatsPoundsForUk`
6. `formatsRupeesWithIndianGrouping` (₹1,23,456.00)
7. `formatsDollars`
8. `roundsHalfEvenOnlyWhenFormatting`

`ReminderPolicyTest`:
9. `sevenDayReminderIsDueWhenCancelByIsSevenDaysAway`
10. `nothingIsDueMoreThanTheLargestOffsetAway`
11. `catchesUpAMissedOffsetWithTheActualDaysLeft`
12. `countsDownToCancelByDateWhenThereIsANoticePeriod`
13. `trialCountsDownToTrialEnd`
14. `noCountdownOnceRenewDecided`
15. `noReminderWhenDecisionIsCancel`
16. `renewsTodayConfirmationForPendingRenewAndDowngrade`
17. `oneDayAndTodayRemindersAreHighPriority`
18. `rejectsOffsetsOutsideOneTo365`

`ReminderMessageTest`:
19. `titleSaysRenewsInDays`
20. `titleSaysTomorrowForOneDay`
21. `titleSaysDaysLeftToCancelWithNotice`
22. `titleSaysTrialEnds`
23. `bodyShowsNativeAmountCycleCancelByAndDecision`
24. `bodyNeverContainsPaymentLabelOrNotes`
25. `clickOpensTheSubscription`

`NtfyNotificationSenderTest` (MockRestServiceServer, like `FrankfurterFxRateProviderTest`):
26. `postsMessageWithTitlePriorityClickAndTags`
27. `sendsBearerTokenWhenConfigured`
28. `retries503ThenSucceeds`
29. `retries429HonouringRetryAfter`
30. `givesUpAfterThreeAttempts`
31. `doesNotRetryOther4xx`

`ReminderServiceTest` (fake `NotificationSender`, Mockito repositories, fixed `Clock`):
32. `sendsDueReminderAndLogsSent`
33. `skipsReminderAlreadySent`
34. `skipsReminderStillSending`
35. `resendsReminderThatFailedLastRun`
36. `logsFailedWhenSenderGivesUp`
37. `carriesOnAfterOneSubscriptionFails`
38. `sendsNothingWhenTopicNotConfigured`
39. `usesLondonDateAt0800DuringBst`

**Integration tests (Testcontainers PostgreSQL + WireMock for ntfy, movable clock):** `ReminderIT`

40. **AC 1:** cancel-by in 7 days → one POST to ntfy with the right headers, and the log row is `SENT` with `attempts = 1`.
41. **AC 2:** run twice → one POST, and still one log row.
42. **AC 3:** ntfy 503 then 200 → two POSTs, and the log is `SENT` with `attempts = 2`.
43. **AC 4:** decision CANCEL → no POST and no log row.
44. ntfy down for all 3 attempts → `FAILED`. The next day's run (same offset still due by catch-up) sends it and the row becomes `SENT`.
45. The unique constraint rejects a second row for the same subscription, renewal date and offset.
46. `POST /settings/test-notification`: 204 when ntfy accepts, 502 when it doesn't, 503 when no topic. `GET /settings/notifications` never returns the topic or token.

**Frontend (Vitest + MSW)**

47. `SettingsPage` shows the ntfy server and "Topic configured" (never the topic itself).
48. "Send test notification" shows "Sent. Check your phone." on success.
49. It shows the problem detail on failure (503 or 502).
50. The navigation has a Settings link.

## UI changes

- **New `SettingsPage` at `/settings`:** a "Notifications" card showing the server, whether a topic is set, the reminder days (14, 7, 1), a short how-to ("subscribe to your topic in the ntfy app"), and a **Send test notification** button with a success or error message. Everything is read-only, because settings come from environment variables in this phase.
- **`Layout.tsx`:** a Settings nav link, in both the sidebar and the mobile tab bar.
- **`src/api/types.ts`:** `NotificationSettings`. **`src/api/hooks.ts`:** `useNotificationSettings` and `useSendTestNotification`.
- **`src/mocks`:** handlers for both endpoints, so `npm run dev:mock` works.

## Design

| Class | Package | Responsibility |
|---|---|---|
| `MoneyFormat` | `currency` | Formats `Money` for display per currency locale, `HALF_EVEN`. The single backend source of money formatting (DRY). |
| `ReminderPolicy` | `notification` | Pure: which offset (if any) is due for a subscription today. No Spring, no clock: `today` is a parameter. |
| `ReminderMessage` | `notification` | Builds title, body, priority and click URL from a subscription and the due offset. |
| `Notification` | `notification` | Record: title, message, priority, click URL, tags. What any sender sends. |
| `NotificationSender` | `notification` | Interface with `void send(Notification)`, throwing `NotificationFailedException`. Small and focused. The second implementation is the test fake (a testing need, as CLAUDE.md allows). |
| `NtfyNotificationSender` | `notification` | Outbound HTTP only: `RestClient`, headers, retry and backoff, outcome logging. |
| `NotificationLog`, `NotificationLogRepository` | `notification` | Persistence of the exactly-once record. |
| `ReminderService` | `notification` | Orchestrates one run: find due reminders, claim, send, mark. Depends on interfaces, repositories and `Clock`. |
| `ReminderJob` | `notification` | `@Scheduled` + run on startup; only triggers the service (same shape as `RenewalRolloverJob`). |
| `NotificationSettingsController` | `notification` | `GET /settings/notifications`, `POST /settings/test-notification`. |
| `JobController` | `common` | Test-only triggers for rollover and reminders (`@ConditionalOnProperty`). |

- **SOLID:**
  - Policy, message, sender, persistence and orchestration change for different reasons, so they're separate classes.
  - New offsets are configuration, not code (open/closed).
  - `NotificationSender` is small, and the fake and the ntfy client honour the same contract: they throw the same exception and neither sends twice on its own (Liskov).
  - Everything uses constructor injection and the injected `Clock`.
- **DRY:**
  - Cancel-by date and status come from `Subscription`.
  - Money formatting lives in one place (`MoneyFormat`).
  - The "live subscriptions" query is reused.
- **KISS:**
  - The retry is a short loop with injectable backoff, not a retry framework.
  - The catch-up rule is one expression ("smallest offset ≥ days left").
- **YAGNI:**
  - No `settings` table, encrypted token storage or editable settings yet. Config comes from environment variables, which the spec's security section already allows for the ntfy token.
  - `ReminderPolicy` is a plain class, not an interface (only one policy).
- **Trade-off:** moving `RolloverJobController` into a shared `JobController` touches F-04 code. It keeps the test-only endpoints in one guarded place, rather than two copies of the same condition.

**New configuration** (README table and `.env.example` updated):

| Variable | Default | Purpose |
|---|---|---|
| `SUBSENSE_NTFY_SERVER` | `https://ntfy.sh` | ntfy server (self-hosting supported) |
| `SUBSENSE_NTFY_TOPIC` | _(empty = reminders off)_ | Your private topic. Generate it with `openssl rand -hex 16` and keep it in `.env` only |
| `SUBSENSE_NTFY_TOKEN` | _(empty)_ | Optional access token for protected topics |
| `SUBSENSE_REMINDER_OFFSETS` | `14,7,1` | Days before cancel-by |
| `SUBSENSE_REMINDER_CRON` | `0 0 8 * * *` | When reminders go out |
| `SUBSENSE_REMINDER_ON_STARTUP` | `true` | Catch up when the app starts |
| `SUBSENSE_APP_URL` | `http://localhost:3000` | Base of the click link in notifications |

**End-to-end setup:**
- The WireMock container (`fx-stub`) gets ntfy mappings for topic `subsense-e2e`: 200 by default, with a WireMock "scenario" giving 503 then 200 for "Spotify".
- `docker-compose.yml` passes `SUBSENSE_NTFY_SERVER` and `SUBSENSE_NTFY_TOPIC` through. They're empty by default.
- **CI** (needs your approval as part of this plan): the e2e job's `env` gets `SUBSENSE_NTFY_SERVER: http://fx-stub:8080` and `SUBSENSE_NTFY_TOPIC: subsense-e2e`.

Docs: README (features, configuration, "Get reminders on your phone" setup), ADR `0004-ntfy-reminders.md` (exactly-once claim-first design, catch-up rule), this plan.

## New dependencies

- **`org.wiremock:wiremock-standalone` (backend, test scope only).**
  - CLAUDE.md and SPEC.md require WireMock for the ntfy integration tests. It exercises real HTTP: headers, retry timing, 429 with `Retry-After`, timeouts.
  - The existing `MockRestServiceServer` can't simulate a slow server.
  - Version 3.13.2, matching the WireMock Docker image already used for e2e.
- Nothing else. No Spring Retry (a short loop is enough) and no ntfy SDK (it's one HTTP POST).

## Risks and open questions

1. **Settings stay read-only for now.** ntfy server, topic, token and offsets come from environment variables, and the Settings page only shows them plus the test button. The spec's `settings` table (editable in the UI, token encrypted at rest) is left for a later "Settings" change. OK?
2. **Catch-up rule:** if a reminder day was missed (app off), send the latest missed reminder late, with the real days left ("renews in 6 days"). The alternative is only ever sending on the exact day, which loses reminders when the laptop is off. A side effect: a subscription you add 10 days before cancel-by gets its "14-day" reminder at the next 08:00, saying "in 10 days". I suggest catch-up.
3. **Crash between "ntfy accepted" and "saved as SENT":** I suggest at most once (never resend a `SENDING` row; log a warning). The spec says "never sent twice", and the window is milliseconds. The alternative, at least once, could double-notify.
4. **CI change:** two env lines in the e2e job, as above. Approving this plan approves that change.
5. **Your phone:** to receive real reminders, install the ntfy app, subscribe to your own random topic, and put it in `.env` as `SUBSENSE_NTFY_TOPIC`. Anyone who knows a topic name on ntfy.sh can read it, so it must stay random and private. It's never committed or logged.
6. **Risk, time zones:** reminders at 08:00 London time. Covered by test 39 and `zone` on `@Scheduled`.
7. **Risk, ntfy.sh rate limits:** a handful of messages a day is far below the free limits. 429s are retried with backoff anyway.

## Implementation notes (post-approval deviations)

### JSON publish instead of URL-per-topic with headers
The plan said publish via `POST /{topic}` with ntfy-specific request headers for title, message, etc. Implemented as `POST /` (JSON body) instead. HTTP headers cannot carry non-Latin-1 bytes (e.g. `₹` in a subscription name); the JSON body has no such restriction. Behaviour is identical from ntfy's perspective.

### Explicit `Optional<NotificationSender>` factory bean removed
An early draft of `NotificationConfiguration` added an explicit `Optional<NotificationSender>` factory bean. Spring's generic type resolution assigned the wrong bean type, causing the sender to always be `Optional.empty()`. Fixed by returning `NotificationSender` (interface, not concrete type) from the `@ConditionalOnProperty` bean and relying on Spring's native `Optional<T>` injection (no explicit factory needed).

### IT tests: `spring.http.client.factory=simple`
Spring Boot 3.2+ defaults to `JdkClientHttpRequestFactory` (Java 21 `HttpClient` with h2c upgrade). WireMock's Jetty does not support h2c and sends `RST_STREAM`, failing every outbound POST. Added `spring.http.client.factory=simple` to `ReminderIT`'s `@SpringBootTest` properties to force `SimpleClientHttpRequestFactory` (HTTP/1.1 `HttpURLConnection`) for that test context only. Production is unaffected.

### `@BeforeAll` guarded to not overwrite `@DynamicPropertySource` WireMock
`@DynamicPropertySource` starts WireMock before the Spring context loads (to register its port) and assigns the instance to the static `ntfy` field. The original `@BeforeAll startNtfy()` unconditionally replaced it with a second WireMock instance on a different port, so `ntfy.resetAll()` / `ntfy.stubFor()` in tests operated on the wrong server. Fixed with `if (ntfy == null)` guard.

### `JobController` replaces `RolloverJobController`
The test-only trigger endpoint was in `com.subsense.subscription.RolloverJobController`. Adding a second trigger for reminders in the same condition-guarded class would mix subscription and notification concerns, so both triggers are now in `com.subsense.common.JobController`. The F-04 rollover endpoint path and behaviour are unchanged.
