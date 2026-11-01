package com.subsense.subscription;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionHistoryRepository extends JpaRepository<DecisionHistory, Long> {

  List<DecisionHistory> findBySubscriptionIdOrderByDecidedAtDescIdDesc(Long subscriptionId);
}
