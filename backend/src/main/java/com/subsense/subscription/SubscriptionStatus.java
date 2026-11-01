package com.subsense.subscription;

/** Derived from type and decision; {@code CANCELLED} is set by the renewal rollover (Phase 2). */
public enum SubscriptionStatus {
  TRIAL,
  ACTIVE,
  CANCELLING,
  CANCELLED
}
