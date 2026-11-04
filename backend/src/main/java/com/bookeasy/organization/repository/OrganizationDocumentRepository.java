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

    /** Documents a reviewer still has to look at: unreviewed uploads of applications under review. */
    @org.springframework.data.jpa.repository.Query("""
            select count(d) from OrganizationDocument d
            where d.status = com.bookeasy.organization.entity.OrganizationDocument.Status.SUBMITTED
              and d.organizationId in (select o.id from Organization o
                                       where o.status = com.bookeasy.organization.entity.OrganizationStatus.PENDING)
            """)
    long countAwaitingReview();
}
