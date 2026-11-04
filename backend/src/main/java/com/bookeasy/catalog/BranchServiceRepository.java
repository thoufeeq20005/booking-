package com.bookeasy.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BranchServiceRepository extends JpaRepository<BranchService, BranchService.Key> {

    List<BranchService> findByServiceIdIn(Collection<Long> serviceIds);

    Optional<BranchService> findByBranchIdAndServiceId(Long branchId, Long serviceId);

    @Modifying(flushAutomatically = true)
    @Query("delete from BranchService b where b.serviceId = :serviceId")
    void deleteAllForService(@Param("serviceId") Long serviceId);
}
