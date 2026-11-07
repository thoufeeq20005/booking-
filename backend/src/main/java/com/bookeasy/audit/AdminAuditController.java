package com.bookeasy.audit;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bookeasy.audit.AuditQueryService.AuditEntryView;
import com.bookeasy.audit.AuditQueryService.Filter;
import com.bookeasy.common.api.ApiResponse;
import com.bookeasy.common.api.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** SUPER_ADMIN only (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@Tag(name = "Admin – Audit log")
public class AdminAuditController {

    private final AuditQueryService service;

    public AdminAuditController(AuditQueryService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Search the audit log, newest first; `to` is exclusive")
    public ApiResponse<PageResponse<AuditEntryView>> search(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(service.search(new Filter(action, entityType, entityId, actorUserId, from, to), page, size));
    }
}
