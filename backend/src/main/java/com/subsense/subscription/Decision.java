package com.subsense.subscription;

/** What to do at the next renewal. */
public enum Decision {
  PENDING,
  RENEW,
  CANCEL,
  DOWNGRADE
}
