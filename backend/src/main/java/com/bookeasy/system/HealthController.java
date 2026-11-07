package com.bookeasy.system;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookeasy.common.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "System")
@SecurityRequirements
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record HealthResponse(String status, String database, Instant checkedAt) {
    }

    @GetMapping
    @Operation(summary = "Application and database health")
    public ResponseEntity<ApiResponse<HealthResponse>> health() {
        boolean dbUp = isDatabaseUp();
        HealthResponse body = new HealthResponse(dbUp ? "UP" : "DEGRADED", dbUp ? "UP" : "DOWN", Instant.now());
        return ResponseEntity.status(dbUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.ok(body));
    }

    private boolean isDatabaseUp() {
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return one != null && one == 1;
        } catch (RuntimeException e) {
            log.warn("Database health check failed: {}", e.getMessage());
            return false;
        }
    }
}
