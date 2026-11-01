INSERT INTO category (name, colour) VALUES
    ('AI Tools',      '#6366F1'),
    ('Entertainment', '#EC4899'),
    ('Security',      '#10B981'),
    ('Career',        '#0EA5E9'),
    ('Shopping',      '#F59E0B'),
    ('Household',     '#8B5CF6'),
    ('Utilities',     '#64748B');

-- Fallback rates so totals work before the first successful refresh (e.g. offline).
-- Replaced by live ECB rates from the Frankfurter API on startup and daily.
INSERT INTO fx_rate (base, quote, rate, rate_date) VALUES
    ('EUR', 'GBP', 0.86000000,  '2026-01-01'),
    ('EUR', 'INR', 100.00000000, '2026-01-01'),
    ('EUR', 'USD', 1.16000000,  '2026-01-01');
