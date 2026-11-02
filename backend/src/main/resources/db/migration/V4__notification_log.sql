-- F-05 ntfy reminders: tracks which push notifications have been sent (idempotency).
CREATE TABLE notification_log (
    id               BIGSERIAL PRIMARY KEY,
    subscription_id  BIGINT       NOT NULL REFERENCES subscription(id),
    renewal_date     DATE         NOT NULL,
    offset_days      INTEGER      NOT NULL CHECK (offset_days BETWEEN 0 AND 365),
    status           VARCHAR(10)  NOT NULL,
    attempts         INTEGER      NOT NULL DEFAULT 0,
    last_error       VARCHAR(500),
    sent_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_notification_log UNIQUE (subscription_id, renewal_date, offset_days)
);

CREATE INDEX idx_notification_log_subscription_id ON notification_log (subscription_id);
