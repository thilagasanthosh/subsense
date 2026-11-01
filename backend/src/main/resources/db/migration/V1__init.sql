CREATE TABLE category (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL UNIQUE,
    colour      VARCHAR(7)   NOT NULL
);

CREATE TABLE subscription (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(100)   NOT NULL,
    vendor              VARCHAR(100)   NOT NULL,
    category_id         BIGINT         NOT NULL REFERENCES category (id),
    type                VARCHAR(20)    NOT NULL,
    billing_cycle       VARCHAR(20)    NOT NULL,
    amount              NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    price_after_trial   NUMERIC(12, 2) CHECK (price_after_trial >= 0),
    currency            VARCHAR(3)     NOT NULL,
    start_date          DATE           NOT NULL,
    next_renewal_date   DATE           NOT NULL,
    notice_days         INTEGER        NOT NULL DEFAULT 0 CHECK (notice_days BETWEEN 0 AND 365),
    auto_renew          BOOLEAN        NOT NULL DEFAULT TRUE,
    payment_label       VARCHAR(50),
    owner               VARCHAR(50)    NOT NULL DEFAULT 'Me',
    decision            VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    downgrade_amount    NUMERIC(12, 2) CHECK (downgrade_amount >= 0),
    usage_rating        VARCHAR(20),
    notes               VARCHAR(1000),
    deleted_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version             BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT renewal_not_before_start CHECK (next_renewal_date >= start_date)
);

CREATE INDEX idx_subscription_active_renewal
    ON subscription (next_renewal_date)
    WHERE deleted_at IS NULL;

CREATE TABLE decision_history (
    id               BIGSERIAL PRIMARY KEY,
    subscription_id  BIGINT         NOT NULL REFERENCES subscription (id),
    decision         VARCHAR(20)    NOT NULL,
    new_amount       NUMERIC(12, 2),
    reason           VARCHAR(500),
    source           VARCHAR(10)    NOT NULL,
    decided_at       TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_decision_history_subscription ON decision_history (subscription_id);

-- Rates are stored per euro (Frankfurter's base), so any pair converts through EUR.
CREATE TABLE fx_rate (
    id         BIGSERIAL PRIMARY KEY,
    base       VARCHAR(3)     NOT NULL,
    quote      VARCHAR(3)     NOT NULL,
    rate       NUMERIC(18, 8) NOT NULL CHECK (rate > 0),
    rate_date  DATE           NOT NULL,
    CONSTRAINT uq_fx_rate UNIQUE (base, quote, rate_date)
);
