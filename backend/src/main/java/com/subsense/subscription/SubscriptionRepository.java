package com.subsense.subscription;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

  List<Subscription> findAllByDeletedAtIsNullOrderByNextRenewalDateAsc();

  Optional<Subscription> findByIdAndDeletedAtIsNull(Long id);
}
