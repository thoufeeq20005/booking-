package com.bookeasy.organization.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bookeasy.organization.entity.OrganizationVerification;

public interface OrganizationVerificationRepository extends JpaRepository<OrganizationVerification, Long> {

    List<OrganizationVerification> findByOrganizationIdOrderByCreatedAtAscIdAsc(Long organizationId);
}
