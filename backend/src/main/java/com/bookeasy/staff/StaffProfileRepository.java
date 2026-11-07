package com.bookeasy.staff;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffProfileRepository extends JpaRepository<StaffProfile, Long> {

    List<StaffProfile> findByOrganizationIdOrderByDisplayNameAsc(Long organizationId);

    Optional<StaffProfile> findByIdAndOrganizationId(Long id, Long organizationId);

    Optional<StaffProfile> findByOrganizationIdAndUserId(Long organizationId, Long userId);

    List<StaffProfile> findByUserIdAndStatus(Long userId, StaffProfile.Status status);

    List<StaffProfile> findByOrganizationIdAndStatusIn(Long organizationId, Collection<StaffProfile.Status> statuses);
}
