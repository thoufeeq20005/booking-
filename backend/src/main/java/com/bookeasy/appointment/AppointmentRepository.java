package com.bookeasy.appointment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.id = :id")
    Optional<Appointment> findByIdForUpdate(@Param("id") Long id);

    Optional<Appointment> findByReference(String reference);

    List<Appointment> findByCustomerIdOrderByStartsAtDesc(Long customerId);

    @Query("""
            select a from Appointment a where a.organizationId = :orgId and a.startsAt >= :from and a.startsAt < :to
              and (:branchId is null or a.branchId = :branchId) order by a.startsAt
            """)
    List<Appointment> forOrganization(@Param("orgId") Long orgId, @Param("branchId") Long branchId,
                                      @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select a from Appointment a where a.staffId in :staffIds and a.startsAt >= :from and a.startsAt < :to
            order by a.startsAt
            """)
    List<Appointment> forStaff(@Param("staffIds") Collection<Long> staffIds, @Param("from") Instant from,
                               @Param("to") Instant to);

    /** Bookings holding time that overlaps [from, to). */
    @Query("""
            select a from Appointment a where a.status in :active and a.startsAt < :to and a.blockedUntil > :from
              and ((a.staffId in :staffIds) or (a.staffId is null and a.branchId = :branchId and a.serviceId = :serviceId))
            """)
    List<Appointment> holding(@Param("active") Collection<Appointment.Status> active, @Param("staffIds") Collection<Long> staffIds,
                              @Param("branchId") Long branchId, @Param("serviceId") Long serviceId,
                              @Param("from") Instant from, @Param("to") Instant to);

    List<Appointment> findByStatusAndCreatedAtBefore(Appointment.Status status, Instant before);
}
