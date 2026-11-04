package com.bookeasy.staff;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffLeaveRepository extends JpaRepository<StaffLeave, Long> {

    /** Leave overlapping [from, to). */
    @Query("select l from StaffLeave l where l.staffId in :staffIds and l.startsAt < :to and l.endsAt > :from")
    List<StaffLeave> overlapping(@Param("staffIds") Collection<Long> staffIds, @Param("from") Instant from,
                                 @Param("to") Instant to);

    List<StaffLeave> findByStaffIdAndEndsAtAfterOrderByStartsAtAsc(Long staffId, Instant after);

    Optional<StaffLeave> findByIdAndStaffId(Long id, Long staffId);
}
