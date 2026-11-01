package com.subsense.subscription;

import java.math.BigDecimal;

/** What one renewal rollover did, for the audit trail. */
public record RolloverOutcome(Decision decision, BigDecimal newAmount, String reason) {}
