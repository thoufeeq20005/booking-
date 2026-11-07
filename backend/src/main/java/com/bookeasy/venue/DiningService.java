package com.bookeasy.venue;

import java.math.BigDecimal;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.appointment.Appointment;
import com.bookeasy.appointment.AppointmentView;
import com.bookeasy.appointment.BookingService;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.bookeasy.file.UploadedFileRepository;

/** Restaurants and cafés: listing, menu, free table slots and table bookings (free, confirmed instantly). */
@Service
public class DiningService {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    /** How long a table is kept for one booking. */
    static final Duration SITTING = Duration.ofMinutes(90);
    static final Duration SLOT_STEP = Duration.ofMinutes(30);
    static final Duration MIN_NOTICE = Duration.ofMinutes(30);
    static final int MAX_PARTY = 10;
    static final int MAX_ADVANCE_DAYS = 30;
    private static final double MAX_RADIUS_KM = 50;
    private static final String ACTIVE = "('PENDING_PAYMENT', 'PENDING_APPROVAL', 'CONFIRMED')";

    private final NamedParameterJdbcTemplate jdbc;
    private final BookingService bookings;
    private final UploadedFileRepository files;
    private final Clock clock;

    public DiningService(NamedParameterJdbcTemplate jdbc, BookingService bookings, UploadedFileRepository files, Clock clock) {
        this.jdbc = jdbc;
        this.bookings = bookings;
        this.files = files;
        this.clock = clock;
    }

    public record Restaurant(long organizationId, String slug, String name, String kind, BigDecimal ratingAvg, int reviewCount,
                             String coverUrl, String cuisines, BigDecimal costForTwo, boolean vegOnly, String offer,
                             long branchId, String locality, String city, double distanceKm) {
    }

    public record MenuItem(long id, String name, String description, BigDecimal price, boolean veg, boolean bestseller) {
    }

    public record MenuSection(String name, List<MenuItem> items) {
    }

    public record DiningDetails(String cuisines, BigDecimal costForTwo, boolean vegOnly, String offer, List<String> features,
                                int maxPartySize, List<MenuSection> menu) {
    }

    public record TableSlot(String time, Instant startsAt, boolean available) {
    }

    @Transactional(readOnly = true)
    public List<Restaurant> nearby(double lat, double lng, double radiusKm, String q) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("lat", lat).addValue("lng", lng)
                .addValue("radiusM", Math.min(Math.max(radiusKm, 1), MAX_RADIUS_KM) * 1000);
        String filter = "";
        if (q != null && !q.isBlank()) {
            filter = " AND (LOWER(o.name) LIKE :q OR LOWER(vp.cuisines) LIKE :q)";
            p.addValue("q", "%" + q.trim().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%");
        }
        List<Object[]> raw = new ArrayList<>();
        jdbc.query("""
                SELECT * FROM (
                  SELECT o.id, o.slug, o.name, s.slug AS kind, o.rating_avg, o.review_count, o.cover_file_id,
                         vp.cuisines, vp.cost_for_two, vp.veg_only, vp.offer_text, b.id AS branch_id, b.locality, b.city,
                         ST_Distance_Sphere(b.location, ST_SRID(POINT(:lng, :lat), 4326)) AS distance_m
                  FROM venue_profiles vp JOIN organizations o ON o.id = vp.organization_id
                  JOIN subcategories s ON s.id = o.subcategory_id
                  JOIN organization_branches b ON b.organization_id = o.id
                  WHERE vp.kind = 'DINING' AND o.verification_status = 'APPROVED' AND b.is_active""" + filter + """
                ) x WHERE distance_m <= :radiusM ORDER BY distance_m LIMIT 60""", p, rs -> {
            raw.add(new Object[]{rs.getLong("id"), rs.getString("slug"), rs.getString("name"), rs.getString("kind"),
                    rs.getBigDecimal("rating_avg"), rs.getInt("review_count"), (Long) rs.getObject("cover_file_id", Long.class),
                    rs.getString("cuisines"), rs.getBigDecimal("cost_for_two"), rs.getBoolean("veg_only"), rs.getString("offer_text"),
                    rs.getLong("branch_id"), rs.getString("locality"), rs.getString("city"), rs.getDouble("distance_m")});
        });
        Map<Long, String> covers = new java.util.HashMap<>();
        files.findAllById(raw.stream().map(r -> (Long) r[6]).filter(java.util.Objects::nonNull).toList())
                .forEach(f -> covers.put(f.getId(), f.publicUrl()));
        return raw.stream().map(r -> new Restaurant((Long) r[0], (String) r[1], (String) r[2], (String) r[3], (BigDecimal) r[4],
                (Integer) r[5], r[6] == null ? null : covers.get((Long) r[6]), (String) r[7], (BigDecimal) r[8], (Boolean) r[9],
                (String) r[10], (Long) r[11], (String) r[12], (String) r[13], Math.round((Double) r[14] / 100.0) / 10.0)).toList();
    }

    @Transactional(readOnly = true)
    public DiningDetails details(String slug) {
        Map<String, Object> vp = profileBySlug(slug);
        long org = ((Number) vp.get("organization_id")).longValue();
        Map<String, List<MenuItem>> sections = new LinkedHashMap<>();
        jdbc.query("SELECT * FROM menu_items WHERE organization_id = :org ORDER BY sort_order, id", Map.of("org", org), rs -> {
            sections.computeIfAbsent(rs.getString("section"), k -> new ArrayList<>()).add(new MenuItem(rs.getLong("id"),
                    rs.getString("name"), rs.getString("description"), rs.getBigDecimal("price"), rs.getBoolean("is_veg"),
                    rs.getBoolean("is_bestseller")));
        });
        String features = (String) vp.get("features");
        return new DiningDetails((String) vp.get("cuisines"), (BigDecimal) vp.get("cost_for_two"),
                Boolean.TRUE.equals(vp.get("veg_only")), (String) vp.get("offer_text"),
                features == null || features.isBlank() ? List.of() : List.of(features.split("\\s*,\\s*")),
                Math.min(MAX_PARTY, ((Number) vp.get("capacity_per_slot")).intValue()),
                sections.entrySet().stream().map(e -> new MenuSection(e.getKey(), e.getValue())).toList());
    }

    @Transactional(readOnly = true)
    public List<TableSlot> slots(long branchId, LocalDate date, int partySize) {
        Map<String, Object> vp = profileByBranch(branchId, false);
        return slots(branchId, date, partySize, ((Number) vp.get("capacity_per_slot")).intValue());
    }

    @Transactional
    public AppointmentView reserve(long customerId, long branchId, Instant startsAt, int partySize, String note) {
        if (partySize < 1 || partySize > MAX_PARTY) {
            throw ApiException.invalidField("partySize", "INVALID", "Choose 1 to " + MAX_PARTY + " guests");
        }
        // Serialises bookings for this restaurant so two people can't take the last table at once.
        Map<String, Object> vp = profileByBranch(branchId, true);
        int capacity = ((Number) vp.get("capacity_per_slot")).intValue();
        LocalDate day = startsAt.atZone(IST).toLocalDate();
        boolean ok = slots(branchId, day, partySize, capacity).stream()
                .anyMatch(s -> s.startsAt().equals(startsAt) && s.available());
        if (!ok) {
            throw new ApiException(ErrorCode.TABLE_UNAVAILABLE);
        }
        String title = "Table for " + partySize;
        Appointment a = bookings.createVenueBooking(new BookingService.VenueBooking(customerId,
                ((Number) vp.get("organization_id")).longValue(), branchId, ((Number) vp.get("booking_service_id")).longValue(),
                startsAt, startsAt.plus(SITTING), title, BigDecimal.ZERO, note, null, partySize));
        return bookings.finishVenueBooking(a);
    }

    // ----- helpers -----

    private List<TableSlot> slots(long branchId, LocalDate date, int partySize, int capacity) {
        Instant now = clock.instant();
        LocalDate today = now.atZone(IST).toLocalDate();
        if (date.isBefore(today) || date.isAfter(today.plusDays(MAX_ADVANCE_DAYS)) || partySize < 1 || partySize > MAX_PARTY) {
            return List.of();
        }
        Integer holiday = jdbc.queryForObject("SELECT COUNT(*) FROM holidays WHERE branch_id = :b AND holiday_date = :d",
                new MapSqlParameterSource("b", branchId).addValue("d", java.sql.Date.valueOf(date)), Integer.class);
        if (holiday != null && holiday > 0) {
            return List.of();
        }
        List<Map<String, Object>> hours = jdbc.queryForList(
                "SELECT opens_at, closes_at, break_start, break_end FROM working_hours WHERE branch_id = :b AND day_of_week = :d",
                new MapSqlParameterSource("b", branchId).addValue("d", date.getDayOfWeek().getValue()));
        if (hours.isEmpty()) {
            return List.of();
        }
        LocalTime opens = ((Time) hours.get(0).get("opens_at")).toLocalTime();
        LocalTime closes = ((Time) hours.get(0).get("closes_at")).toLocalTime();
        LocalTime breakStart = hours.get(0).get("break_start") == null ? null : ((Time) hours.get(0).get("break_start")).toLocalTime();
        LocalTime breakEnd = hours.get(0).get("break_end") == null ? null : ((Time) hours.get(0).get("break_end")).toLocalTime();

        Instant dayStart = date.atStartOfDay(IST).toInstant();
        List<Object[]> booked = new ArrayList<>();
        jdbc.query("SELECT starts_at, party_size FROM appointments WHERE branch_id = :b AND party_size IS NOT NULL"
                        + " AND status IN " + ACTIVE + " AND starts_at >= :from AND starts_at < :until",
                new MapSqlParameterSource("b", branchId).addValue("from", Timestamp.from(dayStart.minus(SITTING)))
                        .addValue("until", Timestamp.from(dayStart.plus(Duration.ofDays(1)))),
                rs -> {
                    booked.add(new Object[]{rs.getTimestamp(1).toInstant(), rs.getInt(2)});
                });

        List<TableSlot> out = new ArrayList<>();
        // Last sitting starts an hour before closing.
        for (LocalTime t = opens; !t.isAfter(closes.minusMinutes(60)); t = t.plus(SLOT_STEP)) {
            if (breakStart != null && !t.isBefore(breakStart) && t.isBefore(breakEnd)) {
                continue;
            }
            Instant start = date.atTime(t).atZone(IST).toInstant();
            if (start.isBefore(now.plus(MIN_NOTICE))) {
                continue;
            }
            Instant end = start.plus(SITTING);
            int covers = 0;
            for (Object[] b : booked) {
                Instant s = (Instant) b[0];
                if (s.isBefore(end) && s.plus(SITTING).isAfter(start)) {
                    covers += (Integer) b[1];
                }
            }
            out.add(new TableSlot(t.toString(), start, covers + partySize <= capacity));
            if (t.isAfter(LocalTime.of(23, 0))) {
                break;
            }
        }
        return out;
    }

    private Map<String, Object> profileBySlug(String slug) {
        List<Map<String, Object>> found = jdbc.queryForList("""
                SELECT vp.* FROM venue_profiles vp JOIN organizations o ON o.id = vp.organization_id
                WHERE o.slug = :slug AND vp.kind = 'DINING' AND o.verification_status = 'APPROVED'""", Map.of("slug", slug));
        if (found.isEmpty()) {
            throw ApiException.notFound("Restaurant");
        }
        return found.get(0);
    }

    private Map<String, Object> profileByBranch(long branchId, boolean forUpdate) {
        List<Map<String, Object>> found = jdbc.queryForList("""
                SELECT vp.* FROM venue_profiles vp JOIN organization_branches b ON b.organization_id = vp.organization_id
                JOIN organizations o ON o.id = vp.organization_id
                WHERE b.id = :b AND b.is_active AND vp.kind = 'DINING' AND o.verification_status = 'APPROVED'"""
                + (forUpdate ? " FOR UPDATE OF vp" : ""), Map.of("b", branchId));
        if (found.isEmpty()) {
            throw ApiException.notFound("Restaurant");
        }
        return found.get(0);
    }
}
