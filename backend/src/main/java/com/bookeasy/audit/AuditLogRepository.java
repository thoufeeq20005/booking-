package com.bookeasy.audit;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, String entityId, Pageable pageable);

    @Query("""
            select a from AuditLog a
            where (:action is null or a.action = :action)
              and (:entityType is null or a.entityType = :entityType)
              and (:entityId is null or a.entityId = :entityId)
              and (:actorUserId is null or a.actorUserId = :actorUserId)
              and (:from is null or a.createdAt >= :from)
              and (:to is null or a.createdAt < :to)
            """)
    Page<AuditLog> search(@Param("action") String action, @Param("entityType") String entityType,
                          @Param("entityId") String entityId, @Param("actorUserId") Long actorUserId,
                          @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}
