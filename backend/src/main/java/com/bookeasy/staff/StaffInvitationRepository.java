package com.bookeasy.staff;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffInvitationRepository extends JpaRepository<StaffInvitation, Long> {

    Optional<StaffInvitation> findByTokenHash(String tokenHash);

    List<StaffInvitation> findByStaffProfileIdOrderByCreatedAtDesc(Long staffProfileId);
}
