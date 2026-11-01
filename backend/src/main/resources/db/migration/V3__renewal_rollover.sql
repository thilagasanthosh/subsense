-- F-04 renewal rollover.
-- The day of month the user chose, so 31 Jan -> 28 Feb -> 31 Mar instead of drifting to the 28th.
ALTER TABLE subscription ADD COLUMN renewal_anchor_day INTEGER;
UPDATE subscription SET renewal_anchor_day = EXTRACT(DAY FROM next_renewal_date);
ALTER TABLE subscription
    ALTER COLUMN renewal_anchor_day SET NOT NULL,
    ADD CONSTRAINT renewal_anchor_day_range CHECK (renewal_anchor_day BETWEEN 1 AND 31);

-- Set when a Cancel takes effect (or a renewal lapses); status is then CANCELLED.
ALTER TABLE subscription ADD COLUMN cancelled_on DATE;
