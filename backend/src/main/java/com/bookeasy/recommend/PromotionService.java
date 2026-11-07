package com.bookeasy.recommend;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.common.exception.ApiException;
import com.bookeasy.organization.service.OrganizationAccess;

/** Offers a business publishes: discounts, free check-ups or trials, membership packages and events. */
@Service
public class PromotionService {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final Set<String> KINDS = Set.of("DISCOUNT", "FREE", "PACKAGE", "EVENT");
    private static final int MAX_PER_BUSINESS = 10;

    private final NamedParameterJdbcTemplate jdbc;
    private final OrganizationAccess access;
    private final Clock clock;

    public PromotionService(NamedParameterJdbcTemplate jdbc, OrganizationAccess access, Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.clock = clock;
    }

    public record PromotionRequest(String title, String details, String kind, Integer percentOff, LocalDate validFrom,
                                   LocalDate validUntil) {
    }

    public record Promotion(long id, String title, String details, String kind, Integer percentOff, LocalDate validFrom,
                            LocalDate validUntil, boolean current) {
    }

    @Transactional(readOnly = true)
    public List<Promotion> forOwner(long userId, long orgId) {
        access.owned(userId, orgId);
        return list(orgId, false);
    }

    /** Current offers of an approved business, for its public page. */
    @Transactional(readOnly = true)
    public List<Promotion> forPublic(String slug) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM organizations WHERE slug = :s AND verification_status = 'APPROVED'",
                Map.of("s", slug), Long.class);
        if (ids.isEmpty()) {
            throw ApiException.notFound("Business");
        }
        return list(ids.get(0), true);
    }

    @Transactional
    public List<Promotion> add(long userId, long orgId, PromotionRequest req) {
        access.ownedForUpdate(userId, orgId);
        String title = text("title", req.title(), 100, true);
        String details = text("details", req.details(), 300, false);
        String kind = req.kind() == null ? "" : req.kind().trim().toUpperCase();
        if (!KINDS.contains(kind)) {
            throw ApiException.invalidField("kind", "INVALID", "Choose discount, free, package or event");
        }
        Integer percent = req.percentOff();
        if (percent != null && (percent < 1 || percent > 90)) {
            throw ApiException.invalidField("percentOff", "INVALID", "A discount is 1% to 90%");
        }
        if (req.validFrom() != null && req.validUntil() != null && req.validFrom().isAfter(req.validUntil())) {
            throw ApiException.invalidField("validUntil", "INVALID", "The end date is before the start date");
        }
        if (req.validUntil() != null && req.validUntil().isBefore(today())) {
            throw ApiException.invalidField("validUntil", "DATE_IN_PAST", "The offer would already have ended");
        }
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM promotions WHERE organization_id = :o", Map.of("o", orgId), Integer.class);
        if (count != null && count >= MAX_PER_BUSINESS) {
            throw ApiException.invalidField("title", "TOO_MANY", "A business can have up to " + MAX_PER_BUSINESS + " offers");
        }
        jdbc.update("""
                INSERT INTO promotions (organization_id, title, details, kind, percent_off, valid_from, valid_until, created_at)
                VALUES (:o, :t, :d, :k, :p, :from, :until, :now)""",
                new MapSqlParameterSource("o", orgId).addValue("t", title).addValue("d", details).addValue("k", kind)
                        .addValue("p", percent)
                        .addValue("from", req.validFrom() == null ? null : java.sql.Date.valueOf(req.validFrom()))
                        .addValue("until", req.validUntil() == null ? null : java.sql.Date.valueOf(req.validUntil()))
                        .addValue("now", Timestamp.from(clock.instant())));
        return list(orgId, false);
    }

    @Transactional
    public List<Promotion> delete(long userId, long orgId, long promotionId) {
        access.ownedForUpdate(userId, orgId);
        if (jdbc.update("DELETE FROM promotions WHERE id = :id AND organization_id = :o",
                new MapSqlParameterSource("id", promotionId).addValue("o", orgId)) == 0) {
            throw ApiException.notFound("Offer");
        }
        return list(orgId, false);
    }

    LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), IST);
    }

    private List<Promotion> list(long orgId, boolean currentOnly) {
        LocalDate today = today();
        return jdbc.query("SELECT * FROM promotions WHERE organization_id = :o ORDER BY created_at DESC, id DESC", Map.of("o", orgId),
                (rs, i) -> {
                    LocalDate from = rs.getDate("valid_from") == null ? null : rs.getDate("valid_from").toLocalDate();
                    LocalDate until = rs.getDate("valid_until") == null ? null : rs.getDate("valid_until").toLocalDate();
                    boolean current = (from == null || !from.isAfter(today)) && (until == null || !until.isBefore(today));
                    return new Promotion(rs.getLong("id"), rs.getString("title"), rs.getString("details"), rs.getString("kind"),
                            (Integer) rs.getObject("percent_off", Integer.class), from, until, current);
                }).stream().filter(p -> !currentOnly || p.current()).toList();
    }

    private static String text(String field, String value, int max, boolean required) {
        String v = value == null ? null : value.trim();
        if (v == null || v.isEmpty()) {
            if (required) {
                throw ApiException.invalidField(field, "REQUIRED", "This field is required");
            }
            return null;
        }
        if (v.length() > max) {
            throw ApiException.invalidField(field, "TOO_LONG", "Use at most " + max + " characters");
        }
        return v;
    }
}
