package com.subsense.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** Audit trail entry: who decided what about a renewal, and when. */
@Entity
@Table(name = "decision_history")
public class DecisionHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "subscription_id", nullable = false)
  private Long subscriptionId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Decision decision;

  @Column(name = "new_amount")
  private BigDecimal newAmount;

  private String reason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DecisionSource source;

  @Column(name = "decided_at", nullable = false)
  private Instant decidedAt;

  protected DecisionHistory() {}

  public DecisionHistory(
      Long subscriptionId,
      Decision decision,
      BigDecimal newAmount,
      String reason,
      DecisionSource source,
      Instant decidedAt) {
    this.subscriptionId = subscriptionId;
    this.decision = decision;
    this.newAmount = newAmount;
    this.reason = reason;
    this.source = source;
    this.decidedAt = decidedAt;
  }

  public Decision getDecision() {
    return decision;
  }

  public BigDecimal getNewAmount() {
    return newAmount;
  }

  public String getReason() {
    return reason;
  }

  public DecisionSource getSource() {
    return source;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }
}
