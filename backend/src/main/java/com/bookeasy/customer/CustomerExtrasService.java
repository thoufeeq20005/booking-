package com.bookeasy.customer;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;

/** Family members, favourites, saved locations and support requests. Everything is scoped to the caller. */
@Service
public class CustomerExtrasService {

    static final int MAX_FAMILY = 10;
    static final int MAX_LOCATIONS = 10;

    private final JdbcTemplate jdbc;
    private final com.bookeasy.notification.NotificationService notifications;
    private final Clock clock;

    public CustomerExtrasService(JdbcTemplate jdbc, com.bookeasy.notification.NotificationService notifications, Clock clock) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.clock = clock;
    }

    // ----- family -----

    public enum Relation { SPOUSE, CHILD, PARENT, SIBLING, OTHER }

    public record FamilyMember(long id, String fullName, Relation relation, LocalDate dateOfBirth) {
    }

    @Transactional(readOnly = true)
    public List<FamilyMember> family(long userId) {
        return jdbc.query("SELECT * FROM family_members WHERE user_id = ? ORDER BY full_name", (rs, i) -> new FamilyMember(
                rs.getLong("id"), rs.getString("full_name"), Relation.valueOf(rs.getString("relation")),
                rs.getDate("date_of_birth") == null ? null : rs.getDate("date_of_birth").toLocalDate()), userId);
    }

    @Transactional
    public FamilyMember addFamily(long userId, String name, Relation relation, LocalDate dob) {
        if (family(userId).size() >= MAX_FAMILY) {
            throw ApiException.invalidField("fullName", "TOO_MANY", "At most 10 family members");
        }
        validateDob(dob);
        jdbc.update("INSERT INTO family_members (user_id, full_name, relation, date_of_birth, created_at) VALUES (?, ?, ?, ?, ?)",
                userId, name.strip(), relation.name(), dob == null ? null : java.sql.Date.valueOf(dob), Timestamp.from(clock.instant()));
        return jdbc.queryForObject("SELECT * FROM family_members WHERE user_id = ? ORDER BY id DESC LIMIT 1", (rs, i) -> new FamilyMember(
                rs.getLong("id"), rs.getString("full_name"), Relation.valueOf(rs.getString("relation")),
                rs.getDate("date_of_birth") == null ? null : rs.getDate("date_of_birth").toLocalDate()), userId);
    }

    @Transactional
    public void removeFamily(long userId, long id) {
        if (jdbc.update("DELETE FROM family_members WHERE id = ? AND user_id = ?", id, userId) == 0) {
            throw ApiException.notFound("Family member");
        }
    }

    private void validateDob(LocalDate dob) {
        if (dob != null && (dob.isAfter(LocalDate.now(clock)) || dob.isBefore(LocalDate.of(1900, 1, 1)))) {
            throw ApiException.invalidField("dateOfBirth", "INVALID", "Enter a real date of birth");
        }
    }

    // ----- favourites -----

    public record Favourite(long organizationId, String slug, String name, BigDecimal ratingAvg, int reviewCount, String city) {
    }

    @Transactional(readOnly = true)
    public List<Favourite> favourites(long userId) {
        return jdbc.query("""
                SELECT o.id, o.slug, o.name, o.rating_avg, o.review_count,
                       (SELECT b.city FROM organization_branches b WHERE b.organization_id = o.id AND b.is_active ORDER BY b.id LIMIT 1) AS city
                FROM favourites f JOIN organizations o ON o.id = f.organization_id
                WHERE f.user_id = ? AND o.verification_status = 'APPROVED' ORDER BY f.created_at DESC""",
                (rs, i) -> new Favourite(rs.getLong("id"), rs.getString("slug"), rs.getString("name"), rs.getBigDecimal("rating_avg"),
                        rs.getInt("review_count"), rs.getString("city")), userId);
    }

    @Transactional
    public void addFavourite(long userId, long orgId) {
        Integer ok = jdbc.queryForObject("SELECT COUNT(*) FROM organizations WHERE id = ? AND verification_status = 'APPROVED'",
                Integer.class, orgId);
        if (ok == null || ok == 0) {
            throw ApiException.notFound("Business");
        }
        try {
            jdbc.update("INSERT INTO favourites (user_id, organization_id, created_at) VALUES (?, ?, ?)", userId, orgId,
                    Timestamp.from(clock.instant()));
        } catch (DuplicateKeyException ignored) {
            // already a favourite
        }
    }

    @Transactional
    public void removeFavourite(long userId, long orgId) {
        jdbc.update("DELETE FROM favourites WHERE user_id = ? AND organization_id = ?", userId, orgId);
    }

    // ----- saved locations -----

    public record SavedLocation(long id, String label, BigDecimal latitude, BigDecimal longitude) {
    }

    @Transactional(readOnly = true)
    public List<SavedLocation> locations(long userId) {
        return jdbc.query("SELECT * FROM saved_locations WHERE user_id = ? ORDER BY created_at DESC", (rs, i) -> new SavedLocation(
                rs.getLong("id"), rs.getString("label"), rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude")), userId);
    }

    @Transactional
    public List<SavedLocation> saveLocation(long userId, String label, BigDecimal lat, BigDecimal lng) {
        if (locations(userId).size() >= MAX_LOCATIONS) {
            throw ApiException.invalidField("label", "TOO_MANY", "At most 10 saved places");
        }
        jdbc.update("INSERT INTO saved_locations (user_id, label, latitude, longitude, created_at) VALUES (?, ?, ?, ?, ?)",
                userId, label.strip(), lat, lng, Timestamp.from(clock.instant()));
        return locations(userId);
    }

    @Transactional
    public void removeLocation(long userId, long id) {
        jdbc.update("DELETE FROM saved_locations WHERE id = ? AND user_id = ?", id, userId);
    }

    // ----- support -----

    public enum SupportStatus { OPEN, IN_PROGRESS, RESOLVED }

    public record SupportRequest(long id, long userId, String userName, String userEmail, Long appointmentId, String reference,
                                 String subject, String message, SupportStatus status, String adminReply, Instant createdAt,
                                 Instant updatedAt) {
    }

    private static final String SUPPORT_SELECT = """
            SELECT s.*, u.full_name, u.email, a.reference FROM support_requests s JOIN users u ON u.id = s.user_id
            LEFT JOIN appointments a ON a.id = s.appointment_id""";

    private static SupportRequest support(java.sql.ResultSet rs) throws java.sql.SQLException {
        long appt = rs.getLong("appointment_id");
        return new SupportRequest(rs.getLong("id"), rs.getLong("user_id"), rs.getString("full_name"), rs.getString("email"),
                rs.wasNull() ? null : appt, rs.getString("reference"), rs.getString("subject"), rs.getString("message"),
                SupportStatus.valueOf(rs.getString("status")), rs.getString("admin_reply"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    @Transactional
    public SupportRequest openRequest(long userId, Long appointmentId, String subject, String message) {
        if (appointmentId != null) {
            Integer own = jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE id = ? AND customer_id = ?", Integer.class,
                    appointmentId, userId);
            if (own == null || own == 0) {
                throw ApiException.invalidField("appointmentId", "INVALID", "Choose one of your bookings");
            }
        }
        Integer recent = jdbc.queryForObject("SELECT COUNT(*) FROM support_requests WHERE user_id = ? AND created_at > ?",
                Integer.class, userId, Timestamp.from(clock.instant().minusSeconds(3600)));
        if (recent != null && recent >= 5) {
            throw new ApiException(ErrorCode.RATE_LIMITED);
        }
        Instant now = clock.instant();
        jdbc.update("INSERT INTO support_requests (user_id, appointment_id, subject, message, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                userId, appointmentId, subject.strip(), message.strip(), Timestamp.from(now), Timestamp.from(now));
        return jdbc.query(SUPPORT_SELECT + " WHERE s.user_id = ? ORDER BY s.id DESC LIMIT 1", (rs, i) -> support(rs), userId).get(0);
    }

    @Transactional(readOnly = true)
    public List<SupportRequest> myRequests(long userId) {
        return jdbc.query(SUPPORT_SELECT + " WHERE s.user_id = ? ORDER BY s.created_at DESC", (rs, i) -> support(rs), userId);
    }

    @Transactional(readOnly = true)
    public List<SupportRequest> adminRequests(SupportStatus status) {
        return jdbc.query(SUPPORT_SELECT + " WHERE (? IS NULL OR s.status = ?) ORDER BY s.created_at ASC LIMIT 200",
                (rs, i) -> support(rs), status == null ? null : status.name(), status == null ? null : status.name());
    }

    @Transactional
    public SupportRequest answer(long id, SupportStatus status, String reply) {
        Instant now = clock.instant();
        int n = jdbc.update("UPDATE support_requests SET status = ?, admin_reply = COALESCE(?, admin_reply), updated_at = ?, "
                        + "resolved_at = CASE WHEN ? = 'RESOLVED' THEN ? ELSE NULL END WHERE id = ?",
                status.name(), reply == null || reply.isBlank() ? null : reply.strip(), Timestamp.from(now), status.name(),
                Timestamp.from(now), id);
        if (n == 0) {
            throw ApiException.notFound("Support request");
        }
        SupportRequest r = jdbc.query(SUPPORT_SELECT + " WHERE s.id = ?", (rs, i) -> support(rs), id).get(0);
        if (reply != null && !reply.isBlank()) {
            notifications.notify(r.userId(), "SUPPORT_REPLY", new Object[]{r.subject()}, "/customer/support");
        }
        return r;
    }
}
