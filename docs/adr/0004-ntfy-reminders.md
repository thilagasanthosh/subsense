# ADR 0004 – ntfy push reminders (F-05)

**Date:** 2026-11-02  
**Status:** Accepted

## Context

F-05 requires push reminders to a phone N days before each subscription renewal, sent exactly once even across restarts and retries. The reminder should link back to the app so the user can act on it.

## Decisions

### 1. ntfy as the push channel

ntfy (ntfy.sh) is a simple, open-source HTTP push service. It requires no SDK, no account for self-hosted use, and works on iOS and Android. The only user action needed is to subscribe to a topic in the mobile app.

Alternatives considered: Pushover (paid, account required), WebPush (needs a service worker and public keys, not applicable here), email (too heavy for one-line reminders).

### 2. JSON publish to the server root, not per-topic URL with headers

ntfy supports two publish methods:
- **URL-per-topic** (`POST /{topic}`) with title, message, etc. as request headers.
- **JSON body** (`POST /`) with all fields, including `topic`, in a JSON object.

The header approach cannot carry non-Latin-1 characters (e.g. `₹` in a subscription title). The JSON approach has no such restriction. We use JSON publish throughout, even though most subscriptions are in Latin-1 currencies, to ensure correctness for all users.

### 3. Claim-first idempotency via `notification_log`

A `notification_log` row with `status = SENDING` is inserted before the ntfy call. This row's unique constraint on `(subscription_id, renewal_date, offset_days)` prevents duplicate rows. Crash recovery: a SENDING row left by a crash is **not retried** (at-most-once guarantee). A FAILED row (after exhausting retries on the same run) **is retried** on the next run. A SENT row is a permanent skip.

Alternative: relying on ntfy's own dedup header — rejected because it would require the caller to track a stable key across restarts, and ntfy's dedup window is 10 minutes, far shorter than the nightly retry window.

### 4. Per-subscription transaction in `ReminderService`

Each subscription's claim-and-send is wrapped in its own transaction. A failure for one subscription (network timeout, unexpected error) rolls back only that subscription's SENDING claim and increments the job's failed counter. Other subscriptions in the same run are not affected.

### 5. `@ConditionalOnProperty` on `NotificationSender`

The `NtfyNotificationSender` bean is only registered when `subsense.ntfy.topic` is non-empty. `ReminderService` and `NotificationSettingsController` accept `Optional<NotificationSender>` via constructor injection. When the topic is not configured, the job logs a debug message and returns `sent=0, failed=0, skipped=0`. Spring's native `Optional<T>` injection provides `Optional.empty()` when the bean is absent.

### 6. HTTP/1.1 in IT tests via `spring.http.client.factory=simple`

Spring Boot 3.2+ defaults to `JdkClientHttpRequestFactory` (Java 21 `HttpClient`), which attempts an h2c (HTTP/2 cleartext) upgrade for every plain HTTP request. WireMock uses Jetty without h2c support and responds with `RST_STREAM`. The IT tests set `spring.http.client.factory=simple` to force `SimpleClientHttpRequestFactory` (Java `HttpURLConnection`, HTTP/1.1 only) for that context only. Production is unaffected and benefits from HTTP/2 with the real ntfy.sh.

## Consequences

- ntfy is a new runtime dependency (external service or self-hosted). The app runs without it; reminders are silently disabled when `SUBSENSE_NTFY_TOPIC` is not set.
- The `notification_log` table is a permanent addition to the schema (V4 migration). It grows by at most one row per subscription per reminder offset per day.
- WireMock was added to the backend test scope (3.13.2) to stub ntfy in integration tests.
