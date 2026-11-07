package com.bookeasy.catalog;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRepository extends JpaRepository<ServiceOffering, Long> {

    List<ServiceOffering> findByOrganizationIdOrderByNameAsc(Long organizationId);

    Optional<ServiceOffering> findByIdAndOrganizationId(Long id, Long organizationId);
}
