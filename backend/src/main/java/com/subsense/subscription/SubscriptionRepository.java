package com.subsense.subscription;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

  List<Subscription> findAllByDeletedAtIsNullOrderByNextRenewalDateAsc();

  Optional<Subscription> findByIdAndDeletedAtIsNull(Long id);

  /** Live subscriptions whose renewal date has arrived (the entity decides what happens). */
  @Query(
      "select s.id from Subscription s where s.deletedAt is null and s.cancelledOn is null"
          + " and s.nextRenewalDate <= :today")
  List<Long> findIdsDueForRollover(LocalDate today);

  /** All non-deleted, non-cancelled subscriptions eligible to receive push reminders. */
  @Query("select s from Subscription s where s.deletedAt is null and s.cancelledOn is null")
  List<Subscription> findAllEligibleForReminders();
}
