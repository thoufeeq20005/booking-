package com.bookeasy.payment;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.audit.AuditService;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.bookeasy.common.security.AuthenticatedUser;
import com.bookeasy.organization.service.OrganizationAccess;

/**
 * Pays businesses their share of completed, paid bookings. With Razorpay Route enabled and a linked account on the
 * business, each booking's share is transferred automatically; otherwise a manual payout is recorded for the
 * finance team to complete and mark as paid.
 */
@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final JdbcTemplate jdbc;
    private final RazorpayClient razorpay;
    private final PaymentProperties properties;
    private final OrganizationAccess access;
    private final AuditService audit;
    private final Clock clock;

    public SettlementService(JdbcTemplate jdbc, RazorpayClient razorpay, PaymentProperties properties,
                             OrganizationAccess access, AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.razorpay = razorpay;
        this.properties = properties;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
    }

    public record SettlementView(long id, long organizationId, String organizationName, long amountPaise, String status,
                                 String razorpayTransferId, String payoutReference, Instant createdAt, Instant paidAt) {
    }

    public record Earnings(long pendingPayoutPaise, long settledPaise, List<SettlementView> settlements) {
    }

    /** Daily at 02:00 India time. */
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Kolkata")
    public void settleScheduled() {
        settle();
    }

    /** Groups every unsettled, completed, paid booking per business into one settlement. */
    @Transactional
    public int settle() {
        List<Map<String, Object>> due = jdbc.queryForList("""
                SELECT c.id, c.organization_id, c.payout_paise, p.razorpay_payment_id, o.razorpay_account_id
                FROM commission_records c
                JOIN appointments a ON a.id = c.appointment_id
                JOIN organizations o ON o.id = c.organization_id
                JOIN payments p ON p.appointment_id = a.id AND p.status = 'CAPTURED'
                WHERE c.settlement_id IS NULL AND a.status = 'COMPLETED' AND c.payout_paise > 0
                ORDER BY c.organization_id, c.id FOR UPDATE""");
        Map<Object, List<Map<String, Object>>> byOrg = new java.util.LinkedHashMap<>();
        due.forEach(r -> byOrg.computeIfAbsent(r.get("organization_id"), k -> new java.util.ArrayList<>()).add(r));
        Instant now = clock.instant();
        int created = 0;
        for (var entry : byOrg.entrySet()) {
            long orgId = ((Number) entry.getKey()).longValue();
            List<Map<String, Object>> rows = entry.getValue();
            long total = rows.stream().mapToLong(r -> ((Number) r.get("payout_paise")).longValue()).sum();
            String account = (String) rows.get(0).get("razorpay_account_id");
            String status = "PENDING_MANUAL";
            String transferId = null;
            if (properties.enabled() && properties.razorpay().routeEnabled() && account != null && !account.isBlank()) {
                try {
                    for (Map<String, Object> r : rows) {
                        transferId = razorpay.transfer((String) r.get("razorpay_payment_id"), account,
                                ((Number) r.get("payout_paise")).longValue());
                    }
                    status = "TRANSFERRED";
                } catch (RuntimeException e) {
                    log.warn("Route transfer failed for organization {}; falling back to a manual payout", orgId, e);
                }
            }
            GeneratedKeyHolder key = new GeneratedKeyHolder();
            final String st = status;
            final String tid = transferId;
            jdbc.update(c -> {
                PreparedStatement ps = c.prepareStatement("INSERT INTO settlements (organization_id, amount_paise, status, "
                        + "razorpay_transfer_id, created_at, paid_at) VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, orgId);
                ps.setLong(2, total);
                ps.setString(3, st);
                ps.setString(4, tid);
                ps.setTimestamp(5, Timestamp.from(now));
                ps.setTimestamp(6, "TRANSFERRED".equals(st) ? Timestamp.from(now) : null);
                return ps;
            }, key);
            long settlementId = key.getKey().longValue();
            for (Map<String, Object> r : rows) {
                jdbc.update("UPDATE commission_records SET settlement_id = ?, updated_at = ? WHERE id = ?", settlementId,
                        Timestamp.from(now), r.get("id"));
            }
            created++;
        }
        return created;
    }

    @Transactional(readOnly = true)
    public List<SettlementView> adminList(String status) {
        return jdbc.query("""
                SELECT s.*, o.name FROM settlements s JOIN organizations o ON o.id = s.organization_id
                WHERE (? IS NULL OR s.status = ?) ORDER BY s.created_at DESC LIMIT 200""",
                (rs, i) -> view(rs), status, status);
    }

    @Transactional
    public SettlementView markPaid(AuthenticatedUser admin, long id, String reference, String ip) {
        int n = jdbc.update("UPDATE settlements SET status = 'PAID', payout_reference = ?, paid_at = ? WHERE id = ? AND status = 'PENDING_MANUAL'",
                reference, Timestamp.from(clock.instant()), id);
        if (n == 0) {
            throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        audit.record(admin.id(), admin.primaryRole(), "SETTLEMENT_PAID", "SETTLEMENT", String.valueOf(id),
                Map.of("reference", reference), ip);
        return jdbc.queryForObject("SELECT s.*, o.name FROM settlements s JOIN organizations o ON o.id = s.organization_id WHERE s.id = ?",
                (rs, i) -> view(rs), id);
    }

    @Transactional
    public void setLinkedAccount(AuthenticatedUser admin, long orgId, String accountId, String ip) {
        String value = accountId == null || accountId.isBlank() ? null : accountId.trim();
        if (value != null && !value.matches("^acc_[A-Za-z0-9]{6,40}$")) {
            throw ApiException.invalidField("accountId", "Pattern", "Razorpay linked account ids look like acc_XXXXXXXX");
        }
        if (jdbc.update("UPDATE organizations SET razorpay_account_id = ? WHERE id = ?", value, orgId) == 0) {
            throw ApiException.notFound("Business");
        }
        audit.record(admin.id(), admin.primaryRole(), "ORGANIZATION_PAYOUT_ACCOUNT_SET", "ORGANIZATION", String.valueOf(orgId),
                Map.of("accountId", value == null ? "" : value), ip);
    }

    @Transactional(readOnly = true)
    public Earnings ownerEarnings(long userId, long orgId) {
        access.owned(userId, orgId);
        Long pending = jdbc.queryForObject("""
                SELECT COALESCE(SUM(c.payout_paise), 0) FROM commission_records c JOIN appointments a ON a.id = c.appointment_id
                WHERE c.organization_id = ? AND c.settlement_id IS NULL AND a.status IN ('CONFIRMED', 'PENDING_APPROVAL', 'COMPLETED')""",
                Long.class, orgId);
        List<SettlementView> list = jdbc.query("""
                SELECT s.*, o.name FROM settlements s JOIN organizations o ON o.id = s.organization_id
                WHERE s.organization_id = ? ORDER BY s.created_at DESC LIMIT 100""", (rs, i) -> view(rs), orgId);
        long settled = list.stream().filter(s -> !"PENDING_MANUAL".equals(s.status())).mapToLong(SettlementView::amountPaise).sum();
        return new Earnings(pending == null ? 0 : pending, settled, list);
    }

    private static SettlementView view(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp paid = rs.getTimestamp("paid_at");
        return new SettlementView(rs.getLong("id"), rs.getLong("organization_id"), rs.getString("name"), rs.getLong("amount_paise"),
                rs.getString("status"), rs.getString("razorpay_transfer_id"), rs.getString("payout_reference"),
                rs.getTimestamp("created_at").toInstant(), paid == null ? null : paid.toInstant());
    }
}
