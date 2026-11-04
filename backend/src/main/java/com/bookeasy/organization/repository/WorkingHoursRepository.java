package com.bookeasy.organization.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bookeasy.organization.entity.WorkingHours;

public interface WorkingHoursRepository extends JpaRepository<WorkingHours, Long> {

    List<WorkingHours> findByBranchIdInOrderByBranchIdAscDayOfWeekAsc(Collection<Long> branchIds);

    /** Bulk delete that runs immediately, so re-inserting the same days can't hit the unique key. */
    @Modifying(flushAutomatically = true)
    @Query("delete from WorkingHours w where w.branchId = :branchId")
    void deleteAllForBranch(@Param("branchId") Long branchId);
}
