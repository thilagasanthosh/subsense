package com.subsense.notification;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

  Optional<NotificationLog> findBySubscriptionIdAndRenewalDateAndOffsetDays(
      Long subscriptionId, LocalDate renewalDate, Integer offsetDays);
}
