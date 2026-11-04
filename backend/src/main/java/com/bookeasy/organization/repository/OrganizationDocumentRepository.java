package com.bookeasy.organization.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bookeasy.organization.entity.OrganizationDocument;

public interface OrganizationDocumentRepository extends JpaRepository<OrganizationDocument, Long> {

    List<OrganizationDocument> findByOrganizationId(Long organizationId);

    Optional<OrganizationDocument> findByOrganizationIdAndRequirementId(Long organizationId, Long requirementId);

    Optional<OrganizationDocument> findByIdAndOrganizationId(Long id, Long organizationId);

    boolean existsByRequirementId(Long requirementId);
}
