package com.bookeasy.staff;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffAvailabilityRepository extends JpaRepository<StaffAvailability, Long> {

    List<StaffAvailability> findByStaffIdInOrderByDayOfWeekAscStartTimeAsc(Collection<Long> staffIds);

    List<StaffAvailability> findByBranchIdAndDayOfWeekAndStaffIdIn(Long branchId, int dayOfWeek,
                                                                   Collection<Long> staffIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from StaffAvailability a where a.staffId = :staffId")
    void deleteAllForStaff(@Param("staffId") Long staffId);
}
