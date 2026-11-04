package com.bookeasy.organization.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bookeasy.organization.entity.Branch;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findByOrganizationIdOrderByIdAsc(Long organizationId);

    List<Branch> findByOrganizationIdInOrderByIdAsc(Collection<Long> organizationIds);

    Optional<Branch> findByIdAndOrganizationId(Long id, Long organizationId);

    long countByOrganizationId(Long organizationId);
}
