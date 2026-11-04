package com.bookeasy.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import com.bookeasy.support.IntegrationTest;

@IntegrationTest
class AuditServiceIT {

    @Autowired
    AuditService auditService;

    @Autowired
    AuditLogRepository repository;

    @Autowired
    TransactionTemplate tx;

    @Test
    void persistsActionWithJsonDetailsInsideCallerTransaction() {
        Long id = tx.execute(status -> auditService.record(7L, "SUPER_ADMIN", "ORG_APPROVED", "ORGANIZATION",
                "42", Map.of("reason", "documents verified"), "127.0.0.1").getId());

        AuditLog saved = repository.findById(id).orElseThrow();
        assertThat(saved.getAction()).isEqualTo("ORG_APPROVED");
        assertThat(saved.getDetails()).contains("documents verified");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void refusesToWriteOutsideATransaction() {
        assertThatThrownBy(() -> auditService.record(1L, "SUPER_ADMIN", "X", "Y", null, null, null))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
