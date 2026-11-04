package com.bookeasy.dashboard;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.audit.AuditQueryService;
import com.bookeasy.audit.AuditQueryService.AuditEntryView;
import com.bookeasy.organization.entity.Organization;
import com.bookeasy.organization.entity.OrganizationStatus;
import com.bookeasy.organization.repository.OrganizationDocumentRepository;
import com.bookeasy.organization.repository.OrganizationRepository;
import com.bookeasy.user.entity.RoleCode;
import com.bookeasy.user.entity.UserStatus;
import com.bookeasy.user.repository.UserRepository;

/** Platform numbers for the Super Admin overview. Everything is counted live from the database. */
@Service
public class AdminDashboardService {

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final OrganizationDocumentRepository documents;
    private final AuditQueryService audit;
    private final Clock clock;

    public AdminDashboardService(UserRepository users, OrganizationRepository organizations,
                                 OrganizationDocumentRepository documents, AuditQueryService audit, Clock clock) {
        this.users = users;
        this.organizations = organizations;
        this.documents = documents;
        this.audit = audit;
        this.clock = clock;
    }

    public record UserStats(long total, long newLast7Days, long suspended, Map<RoleCode, Long> byRole) {
    }

    public record ReviewQueue(long pending, Instant oldestSubmittedAt, Long oldestOrganizationId,
                              String oldestOrganizationName, long documentsAwaitingReview) {
    }

    public record AdminDashboard(UserStats users, Map<OrganizationStatus, Long> organizations, ReviewQueue reviewQueue,
                                 List<AuditEntryView> recentActivity) {
    }

    @Transactional(readOnly = true)
    public AdminDashboard overview() {
        Map<RoleCode, Long> byRole = new EnumMap<>(RoleCode.class);
        for (RoleCode role : RoleCode.values()) {
            byRole.put(role, users.countWithRole(role));
        }
        UserStats userStats = new UserStats(users.count(),
                users.countByCreatedAtGreaterThanEqual(clock.instant().minus(Duration.ofDays(7))),
                users.countByStatus(UserStatus.SUSPENDED), byRole);

        Map<OrganizationStatus, Long> orgs = new EnumMap<>(OrganizationStatus.class);
        for (OrganizationStatus s : OrganizationStatus.values()) {
            orgs.put(s, 0L);
        }
        for (Object[] row : organizations.countByStatus()) {
            orgs.put((OrganizationStatus) row[0], ((Number) row[1]).longValue());
        }

        Organization oldest = organizations.findFirstByStatusOrderBySubmittedAtAsc(OrganizationStatus.PENDING)
                .orElse(null);
        ReviewQueue queue = new ReviewQueue(orgs.get(OrganizationStatus.PENDING),
                oldest == null ? null : oldest.getSubmittedAt(), oldest == null ? null : oldest.getId(),
                oldest == null ? null : oldest.getName(), documents.countAwaitingReview());

        return new AdminDashboard(userStats, orgs, queue, audit.recent(10));
    }
}
