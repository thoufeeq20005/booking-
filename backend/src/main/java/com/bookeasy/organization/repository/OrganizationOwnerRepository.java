package com.bookeasy.organization.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bookeasy.organization.entity.OrganizationOwner;

public interface OrganizationOwnerRepository extends JpaRepository<OrganizationOwner, OrganizationOwner.Key> {

    boolean existsByOrganizationIdAndUserId(Long organizationId, Long userId);

    List<OrganizationOwner> findByOrganizationId(Long organizationId);

    List<OrganizationOwner> findByOrganizationIdIn(Collection<Long> organizationIds);
}
