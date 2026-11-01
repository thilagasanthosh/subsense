package com.subsense.currency;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface FxRateRepository extends JpaRepository<FxRate, Long> {

  @Query("select max(r.rateDate) from FxRate r")
  Optional<LocalDate> findLatestRateDate();

  List<FxRate> findByRateDate(LocalDate rateDate);

  boolean existsByRateDate(LocalDate rateDate);
}
