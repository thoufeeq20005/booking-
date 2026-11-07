package com.bookeasy.audit;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Records an action inside the caller's transaction, so the audit row commits or rolls back with the change.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditLog record(Long actorUserId, String actorRole, String action, String entityType, String entityId,
                           Map<String, ?> details, String ipAddress) {
        return repository.save(new AuditLog(actorUserId, actorRole, action, entityType, entityId,
                toJson(details), ipAddress));
    }

    private String toJson(Map<String, ?> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Audit details are not serialisable", e);
        }
    }
}
