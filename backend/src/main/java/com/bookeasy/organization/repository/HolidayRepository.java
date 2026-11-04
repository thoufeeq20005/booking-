package com.bookeasy.organization.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bookeasy.organization.entity.Holiday;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    List<Holiday> findByBranchIdAndDateGreaterThanEqualOrderByDateAsc(Long branchId, LocalDate from);

    List<Holiday> findTop10ByBranchIdInAndDateGreaterThanEqualOrderByDateAsc(java.util.Collection<Long> branchIds,
                                                                             LocalDate from);

    Optional<Holiday> findByIdAndBranchId(Long id, Long branchId);

    boolean existsByBranchIdAndDate(Long branchId, LocalDate date);

    long countByBranchIdAndDateGreaterThanEqual(Long branchId, LocalDate from);
}
