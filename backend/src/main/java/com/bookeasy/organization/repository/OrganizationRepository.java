package com.bookeasy.organization.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bookeasy.organization.entity.Organization;
import com.bookeasy.organization.entity.OrganizationStatus;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    /** Row lock for every state-changing operation, so a review can't interleave with an owner edit. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Organization o where o.id = :id")
    Optional<Organization> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select o from Organization o
            where o.id in (select ow.organizationId from OrganizationOwner ow where ow.userId = :userId)
            order by o.createdAt desc
            """)
    List<Organization> findOwnedBy(@Param("userId") Long userId);

    @Query("""
            select count(o) from Organization o
            where o.status in :statuses
              and o.id in (select ow.organizationId from OrganizationOwner ow where ow.userId = :userId)
            """)
    long countOwnedInStatuses(@Param("userId") Long userId,
                              @Param("statuses") Collection<OrganizationStatus> statuses);

    boolean existsBySlug(String slug);

    Optional<Organization> findBySlug(String slug);

    @Query("select o.status, count(o) from Organization o group by o.status")
    List<Object[]> countByStatus();

    Optional<Organization> findFirstByStatusOrderBySubmittedAtAsc(OrganizationStatus status);

    boolean existsBySubcategoryIdIn(Collection<Long> subcategoryIds);

    @Query("""
            select o from Organization o
            where (:status is null or o.status = :status)
              and (:q is null or lower(o.name) like lower(concat('%', :q, '%')) or o.slug like concat('%', :q, '%'))
            """)
    Page<Organization> search(@Param("q") String q, @Param("status") OrganizationStatus status, Pageable pageable);
}
