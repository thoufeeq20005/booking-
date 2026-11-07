package com.bookeasy.audit;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.bookeasy.common.api.PageResponse;
import com.bookeasy.user.entity.User;
import com.bookeasy.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Read side of the audit log for the admin console. The log itself stays append-only. */
@Service
public class AuditQueryService {

    private final AuditLogRepository repository;
    private final UserRepository users;
    private final ObjectMapper objectMapper;

    public AuditQueryService(AuditLogRepository repository, UserRepository users, ObjectMapper objectMapper) {
        this.repository = repository;
        this.users = users;
        this.objectMapper = objectMapper;
    }

    public record AuditEntryView(long id, Instant createdAt, Long actorUserId, String actorName, String actorRole,
                                 String action, String entityType, String entityId, JsonNode details,
                                 String ipAddress) {
    }

    public record Filter(String action, String entityType, String entityId, Long actorUserId, Instant from,
                         Instant to) {
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEntryView> search(Filter f, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<AuditLog> result = repository.search(blank(f.action()), blank(f.entityType()), blank(f.entityId()),
                f.actorUserId(), f.from(), f.to(), pageable);
        List<AuditEntryView> views = views(result.getContent());
        return new PageResponse<>(views, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<AuditEntryView> recent(int count) {
        return views(repository.findAll(PageRequest.of(0, count,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")))).getContent());
    }

    private List<AuditEntryView> views(List<AuditLog> logs) {
        Map<Long, String> names = new HashMap<>();
        users.findAllById(logs.stream().map(AuditLog::getActorUserId).filter(java.util.Objects::nonNull).distinct()
                .toList()).forEach((User u) -> names.put(u.getId(), u.getFullName()));
        return logs.stream().map(a -> new AuditEntryView(a.getId(), a.getCreatedAt(), a.getActorUserId(),
                a.getActorUserId() == null ? null : names.get(a.getActorUserId()), a.getActorRole(), a.getAction(),
                a.getEntityType(), a.getEntityId(), parse(a.getDetails()), a.getIpAddress())).toList();
    }

    private JsonNode parse(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            return objectMapper.getNodeFactory().textNode(json);
        }
    }

    private static String blank(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
