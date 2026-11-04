package com.bookeasy.review;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.appointment.Appointment;
import com.bookeasy.appointment.AppointmentRepository;
import com.bookeasy.audit.AuditService;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.bookeasy.common.ratelimit.RateLimiter;
import com.bookeasy.common.security.AuthenticatedUser;
import com.bookeasy.organization.service.OrganizationAccess;

/**
 * Reviews: only the customer of a completed booking can review it, once. The business replies; anyone signed in can
 * report; Super Admins hide or restore. The business's rating is recomputed from visible reviews on every change.
 */
@Service
public class ReviewService {

    private final JdbcTemplate jdbc;
    private final AppointmentRepository appointments;
    private final OrganizationAccess access;
    private final AuditService audit;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public ReviewService(JdbcTemplate jdbc, AppointmentRepository appointments, OrganizationAccess access, AuditService audit,
                         RateLimiter rateLimiter, Clock clock) {
        this.jdbc = jdbc;
        this.appointments = appointments;
        this.access = access;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    public record ReviewView(long id, long appointmentId, long organizationId, String organizationName, String authorName,
                             String serviceName, int rating, String comment, String status, String ownerReply, Instant repliedAt,
                             int reportCount, Instant createdAt) {
    }

    /** Public pages show first name and initial only. */
    private static final RowMapper<ReviewView> PUBLIC = (rs, i) -> {
        String name = rs.getString("full_name");
        String[] parts = name == null ? new String[0] : name.trim().split("\\s+");
        String author = parts.length == 0 ? "" : parts.length == 1 ? parts[0] : parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
        return map(rs, author);
    };

    private static final RowMapper<ReviewView> FULL = (rs, i) -> map(rs, rs.getString("full_name"));

    private static ReviewView map(java.sql.ResultSet rs, String author) throws java.sql.SQLException {
        Timestamp replied = rs.getTimestamp("replied_at");
        return new ReviewView(rs.getLong("id"), rs.getLong("appointment_id"), rs.getLong("organization_id"), rs.getString("org_name"),
                author, rs.getString("service_name"), rs.getInt("rating"), rs.getString("comment"), rs.getString("status"),
                rs.getString("owner_reply"), replied == null ? null : replied.toInstant(), rs.getInt("report_count"),
                rs.getTimestamp("created_at").toInstant());
    }

    private static final String SELECT = """
            SELECT r.*, u.full_name, o.name AS org_name, a.service_name FROM reviews r
            JOIN users u ON u.id = r.customer_id JOIN organizations o ON o.id = r.organization_id
            JOIN appointments a ON a.id = r.appointment_id""";

    @Transactional
    public ReviewView create(long customerId, long appointmentId, int rating, String comment) {
        Appointment a = appointments.findById(appointmentId).filter(x -> x.getCustomerId() == customerId)
                .orElseThrow(() -> ApiException.notFound("Booking"));
        if (a.getStatus() != Appointment.Status.COMPLETED) {
            throw new ApiException(ErrorCode.REVIEW_NOT_ALLOWED);
        }
        if (rating < 1 || rating > 5) {
            throw ApiException.invalidField("rating", "Range", "Choose 1 to 5 stars");
        }
        Instant now = clock.instant();
        try {
            jdbc.update("""
                    INSERT INTO reviews (appointment_id, organization_id, customer_id, staff_id, rating, comment, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", a.getId(), a.getOrganizationId(), customerId, a.getStaffId(), rating,
                    blank(comment), Timestamp.from(now), Timestamp.from(now));
        } catch (DuplicateKeyException e) {
            throw new ApiException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }
        recompute(a.getOrganizationId());
        return jdbc.queryForObject(SELECT + " WHERE r.appointment_id = ?", FULL, a.getId());
    }

    @Transactional(readOnly = true)
    public List<ReviewView> forBusinessPage(String slug, int limit) {
        return jdbc.query(SELECT + " WHERE o.slug = ? AND o.verification_status = 'APPROVED' AND r.status = 'VISIBLE' "
                + "ORDER BY r.created_at DESC LIMIT ?", PUBLIC, slug, Math.min(Math.max(limit, 1), 100));
    }

    @Transactional(readOnly = true)
    public List<ReviewView> forOwner(long userId, long orgId) {
        access.owned(userId, orgId);
        return jdbc.query(SELECT + " WHERE r.organization_id = ? ORDER BY r.created_at DESC LIMIT 200", FULL, orgId);
    }

    @Transactional
    public ReviewView reply(long userId, long orgId, long reviewId, String reply) {
        access.owned(userId, orgId);
        String text = blank(reply);
        if (text == null) {
            throw ApiException.invalidField("reply", "NotBlank", "Write a reply");
        }
        if (jdbc.update("UPDATE reviews SET owner_reply = ?, replied_at = ?, updated_at = ? WHERE id = ? AND organization_id = ?",
                text, Timestamp.from(clock.instant()), Timestamp.from(clock.instant()), reviewId, orgId) == 0) {
            throw ApiException.notFound("Review");
        }
        return jdbc.queryForObject(SELECT + " WHERE r.id = ?", FULL, reviewId);
    }

    @Transactional
    public void report(long userId, long reviewId, String reason) {
        rateLimiter.hit("report:user:" + userId, 20, java.time.Duration.ofDays(1));
        String why = blank(reason);
        if (why == null) {
            throw ApiException.invalidField("reason", "NotBlank", "Tell us what's wrong");
        }
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM reviews WHERE id = ? AND status = 'VISIBLE'", Integer.class, reviewId);
        if (exists == null || exists == 0) {
            throw ApiException.notFound("Review");
        }
        try {
            jdbc.update("INSERT INTO review_reports (review_id, reporter_id, reason, created_at) VALUES (?, ?, ?, ?)",
                    reviewId, userId, why.length() > 300 ? why.substring(0, 300) : why, Timestamp.from(clock.instant()));
            jdbc.update("UPDATE reviews SET report_count = report_count + 1 WHERE id = ?", reviewId);
        } catch (DuplicateKeyException ignored) {
            // one report per person
        }
    }

    @Transactional(readOnly = true)
    public List<ReviewView> moderationQueue(boolean reportedOnly) {
        return jdbc.query(SELECT + (reportedOnly ? " WHERE r.report_count > 0" : "")
                + " ORDER BY r.report_count DESC, r.created_at DESC LIMIT 200", FULL);
    }

    @Transactional
    public ReviewView moderate(AuthenticatedUser admin, long reviewId, boolean hide, String note, String ip) {
        Map<String, Object> row = jdbc.queryForList("SELECT organization_id FROM reviews WHERE id = ?", reviewId).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Review"));
        jdbc.update("UPDATE reviews SET status = ?, moderation_note = ?, report_count = CASE WHEN ? THEN report_count ELSE 0 END, "
                        + "updated_at = ? WHERE id = ?", hide ? "HIDDEN" : "VISIBLE", blank(note), hide,
                Timestamp.from(clock.instant()), reviewId);
        recompute(((Number) row.get("organization_id")).longValue());
        audit.record(admin.id(), admin.primaryRole(), hide ? "REVIEW_HIDDEN" : "REVIEW_RESTORED", "REVIEW", String.valueOf(reviewId),
                note == null ? Map.of() : Map.of("note", note), ip);
        return jdbc.queryForObject(SELECT + " WHERE r.id = ?", FULL, reviewId);
    }

    /** Rating = average of visible reviews, rounded to two decimals. */
    private void recompute(long orgId) {
        jdbc.update("""
                UPDATE organizations o SET
                  rating_avg = (SELECT COALESCE(ROUND(AVG(r.rating), 2), 0) FROM reviews r WHERE r.organization_id = o.id AND r.status = 'VISIBLE'),
                  review_count = (SELECT COUNT(*) FROM reviews r WHERE r.organization_id = o.id AND r.status = 'VISIBLE')
                WHERE o.id = ?""", orgId);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
