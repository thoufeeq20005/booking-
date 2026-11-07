package com.bookeasy.venue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.appointment.Appointment;
import com.bookeasy.appointment.AppointmentRepository;
import com.bookeasy.appointment.AppointmentView;
import com.bookeasy.appointment.BookingService;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.bookeasy.file.UploadedFileRepository;
import com.bookeasy.platform.PlatformConfigService;

/**
 * Restaurants and cafés: listing, menu, offers, table slots, table bookings and bill payments.
 *
 * <p>Booking a table costs a cover charge per guest (₹10 at cafés, ₹25 at premium places; platform settings), paid
 * online and credited against the final bill. The chosen offer is a discount on that bill. BookEasy adds no
 * platform fee to either.
 */
@Service
public class DiningService {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    /** How long a table is kept for one booking. */
    static final Duration SITTING = Duration.ofMinutes(90);
    static final Duration SLOT_STEP = Duration.ofMinutes(30);
    static final Duration MIN_NOTICE = Duration.ofMinutes(30);
    /** Lunch is before this time, dinner from it. */
    static final LocalTime DINNER_FROM = LocalTime.of(16, 0);
    static final int MAX_PARTY = 10;
    static final int MAX_ADVANCE_DAYS = 30;
    static final BigDecimal MAX_BILL = BigDecimal.valueOf(500_000);
    public static final String COVER_CAFE = "dining.cover_per_guest.cafe";
    public static final String COVER_PREMIUM = "dining.cover_per_guest.premium";
    private static final double MAX_RADIUS_KM = 50;
    private static final String ACTIVE = "('PENDING_PAYMENT', 'PENDING_APPROVAL', 'CONFIRMED')";

    private final NamedParameterJdbcTemplate jdbc;
    private final BookingService bookings;
    private final AppointmentRepository appointments;
    private final UploadedFileRepository files;
    private final PlatformConfigService settings;
    private final Clock clock;

    public DiningService(NamedParameterJdbcTemplate jdbc, BookingService bookings, AppointmentRepository appointments,
                         UploadedFileRepository files, PlatformConfigService settings, Clock clock) {
        this.jdbc = jdbc;
        this.bookings = bookings;
        this.appointments = appointments;
        this.files = files;
        this.settings = settings;
        this.clock = clock;
    }

    public enum Meal { LUNCH, DINNER, ALL_DAY }

    public record Offer(long id, String title, int percent, Meal meal) {
        boolean appliesTo(Meal m) {
            return meal == Meal.ALL_DAY || meal == m;
        }
    }

    public record Restaurant(long organizationId, String slug, String name, String kind, BigDecimal ratingAvg, int reviewCount,
                             String coverUrl, String cuisines, BigDecimal costForTwo, boolean vegOnly, String offer,
                             long branchId, String locality, String city, double distanceKm, String tier, Integer bestOfferPercent,
                             String bestOfferTitle) {
    }

    public record MenuItem(long id, String name, String description, BigDecimal price, boolean veg, boolean bestseller) {
    }

    public record MenuSection(String name, List<MenuItem> items) {
    }

    public record DiningDetails(String cuisines, BigDecimal costForTwo, boolean vegOnly, String offer, List<String> features,
                                int maxPartySize, List<MenuSection> menu, String tier, BigDecimal coverPerGuest,
                                List<Offer> offers) {
    }

    public record TableSlot(String time, Instant startsAt, boolean available, Meal meal) {
    }

    /** How a bill is settled: bill − offer discount − cover already paid = to pay now (never below zero). */
    public record BillQuote(BigDecimal billAmount, String offerTitle, Integer offerPercent, BigDecimal discount,
                            BigDecimal coverCredit, BigDecimal payable, Long bookingId) {
    }

    // ----- browsing -----

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
                         vp.cuisines, vp.cost_for_two, vp.veg_only, vp.offer_text, vp.dining_tier, b.id AS branch_id, b.locality, b.city,
                         ST_Distance_Sphere(b.location, ST_SRID(POINT(:lng, :lat), 4326)) AS distance_m
                  FROM venue_profiles vp JOIN organizations o ON o.id = vp.organization_id
                  JOIN subcategories s ON s.id = o.subcategory_id
                  JOIN organization_branches b ON b.organization_id = o.id
                  WHERE vp.kind = 'DINING' AND o.verification_status = 'APPROVED' AND b.is_active""" + filter + """
                ) x WHERE distance_m <= :radiusM ORDER BY distance_m LIMIT 60""", p, rs -> {
            raw.add(new Object[]{rs.getLong("id"), rs.getString("slug"), rs.getString("name"), rs.getString("kind"),
                    rs.getBigDecimal("rating_avg"), rs.getInt("review_count"), (Long) rs.getObject("cover_file_id", Long.class),
                    rs.getString("cuisines"), rs.getBigDecimal("cost_for_two"), rs.getBoolean("veg_only"), rs.getString("offer_text"),
                    rs.getLong("branch_id"), rs.getString("locality"), rs.getString("city"), rs.getDouble("distance_m"),
                    rs.getString("dining_tier")});
        });
        Map<Long, String> covers = new java.util.HashMap<>();
        files.findAllById(raw.stream().map(r -> (Long) r[6]).filter(java.util.Objects::nonNull).toList())
                .forEach(f -> covers.put(f.getId(), f.publicUrl()));
        Map<Long, Offer> best = new java.util.HashMap<>();
        List<Long> orgIds = raw.stream().map(r -> (Long) r[0]).distinct().toList();
        if (!orgIds.isEmpty()) {
            jdbc.query("SELECT * FROM dining_offers WHERE organization_id IN (:ids)", Map.of("ids", orgIds), rs -> {
                Offer o = offer(rs);
                best.merge(rs.getLong("organization_id"), o, (a, b) -> a.percent() >= b.percent() ? a : b);
            });
        }
        return raw.stream().map(r -> {
            Offer o = best.get((Long) r[0]);
            return new Restaurant((Long) r[0], (String) r[1], (String) r[2], (String) r[3], (BigDecimal) r[4],
                    (Integer) r[5], r[6] == null ? null : covers.get((Long) r[6]), (String) r[7], (BigDecimal) r[8], (Boolean) r[9],
                    (String) r[10], (Long) r[11], (String) r[12], (String) r[13], Math.round((Double) r[14] / 100.0) / 10.0,
                    (String) r[15], o == null ? null : o.percent(), o == null ? null : o.title());
        }).toList();
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
        String tier = (String) vp.get("dining_tier");
        return new DiningDetails((String) vp.get("cuisines"), (BigDecimal) vp.get("cost_for_two"),
                Boolean.TRUE.equals(vp.get("veg_only")), (String) vp.get("offer_text"),
                features == null || features.isBlank() ? List.of() : List.of(features.split("\\s*,\\s*")),
                Math.min(MAX_PARTY, ((Number) vp.get("capacity_per_slot")).intValue()),
                sections.entrySet().stream().map(e -> new MenuSection(e.getKey(), e.getValue())).toList(),
                tier, coverPerGuest(tier), offers(org));
    }

    @Transactional(readOnly = true)
    public List<TableSlot> slots(long branchId, LocalDate date, int partySize) {
        Map<String, Object> vp = profileByBranch(branchId, false);
        return slots(branchId, date, partySize, ((Number) vp.get("capacity_per_slot")).intValue());
    }

    // ----- table bookings -----

    @Transactional
    public AppointmentView reserve(long customerId, long branchId, Instant startsAt, int partySize, String note, Long offerId) {
        if (partySize < 1 || partySize > MAX_PARTY) {
            throw ApiException.invalidField("partySize", "INVALID", "Choose 1 to " + MAX_PARTY + " guests");
        }
        // Serialises bookings for this restaurant so two people can't take the last table at once.
        Map<String, Object> vp = profileByBranch(branchId, true);
        long org = ((Number) vp.get("organization_id")).longValue();
        int capacity = ((Number) vp.get("capacity_per_slot")).intValue();
        LocalDate day = startsAt.atZone(IST).toLocalDate();
        boolean ok = slots(branchId, day, partySize, capacity).stream()
                .anyMatch(s -> s.startsAt().equals(startsAt) && s.available());
        if (!ok) {
            throw new ApiException(ErrorCode.TABLE_UNAVAILABLE);
        }
        Offer offer = null;
        if (offerId != null) {
            Meal meal = mealAt(startsAt);
            offer = offers(org).stream().filter(o -> o.id() == offerId && o.appliesTo(meal)).findFirst()
                    .orElseThrow(() -> ApiException.invalidField("offerId", "INVALID", "That offer isn't available at this time"));
        }
        BigDecimal cover = coverPerGuest((String) vp.get("dining_tier")).multiply(BigDecimal.valueOf(partySize));
        String title = "Table for " + partySize + (offer == null ? "" : " · " + offer.title());
        Appointment a = bookings.createVenueBooking(new BookingService.VenueBooking(customerId, org, branchId,
                ((Number) vp.get("booking_service_id")).longValue(), startsAt, startsAt.plus(SITTING),
                title.length() > 150 ? title.substring(0, 150) : title, cover, note, null, partySize, true));
        if (offer != null) {
            a.withOffer(offer.title(), offer.percent());
        }
        return bookings.finishVenueBooking(a);
    }

    // ----- bill payments -----

    /** What paying a bill of {@code amount} here would cost now, using today's table booking when there is one. */
    @Transactional(readOnly = true)
    public BillQuote quote(long customerId, long branchId, BigDecimal amount, Long bookingId) {
        Map<String, Object> vp = profileByBranch(branchId, false);
        return quote(customerId, ((Number) vp.get("organization_id")).longValue(), branchId, amount, bookingId);
    }

    @Transactional
    public AppointmentView payBill(long customerId, long branchId, BigDecimal amount, Long bookingId) {
        Map<String, Object> vp = profileByBranch(branchId, true);
        long org = ((Number) vp.get("organization_id")).longValue();
        BillQuote q = quote(customerId, org, branchId, amount, bookingId);
        Instant now = clock.instant();
        Appointment a = bookings.createVenueBooking(new BookingService.VenueBooking(customerId, org, branchId,
                ((Number) vp.get("booking_service_id")).longValue(), now, now.plus(Duration.ofMinutes(5)),
                "Bill payment" + (q.offerTitle() == null ? "" : " · " + q.offerTitle()), q.payable(), null, null, null, true));
        a.withOffer(q.offerTitle(), q.offerPercent());
        a.asBill(q.billAmount(), q.discount(), q.coverCredit(), q.bookingId());
        return bookings.finishVenueBooking(a);
    }

    private BillQuote quote(long customerId, long org, long branchId, BigDecimal amount, Long bookingId) {
        if (amount == null || amount.signum() <= 0 || amount.compareTo(MAX_BILL) > 0) {
            throw ApiException.invalidField("amount", "INVALID", "Enter the bill amount (up to ₹5,00,000)");
        }
        BigDecimal bill = amount.setScale(2, RoundingMode.HALF_UP);
        Instant now = clock.instant();
        Optional<Appointment> booking = bookingId != null
                ? appointments.findById(bookingId).filter(b -> usableBooking(b, customerId, branchId, now))
                : todaysBooking(customerId, branchId, now);
        if (bookingId != null && booking.isEmpty()) {
            throw ApiException.invalidField("bookingId", "INVALID", "Pick one of today's table bookings at this restaurant");
        }
        String title;
        Integer percent;
        if (booking.isPresent() && booking.get().getOfferPercent() != null) {
            title = booking.get().getOfferTitle();
            percent = booking.get().getOfferPercent();
        } else {
            Meal meal = mealAt(now);
            Optional<Offer> best = offers(org).stream().filter(o -> o.appliesTo(meal)).max(Comparator.comparingInt(Offer::percent));
            title = best.map(Offer::title).orElse(null);
            percent = best.map(Offer::percent).orElse(null);
        }
        BigDecimal discount = percent == null ? BigDecimal.ZERO.setScale(2)
                : bill.multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal credit = booking.filter(this::coverPaid).map(Appointment::getServicePrice).orElse(BigDecimal.ZERO).setScale(2);
        BigDecimal payable = bill.subtract(discount).subtract(credit).max(BigDecimal.ZERO).setScale(2);
        return new BillQuote(bill, title, percent, discount, credit, payable, booking.map(Appointment::getId).orElse(null));
    }

    /** Today's confirmed or completed table booking here, by this customer, whose cover hasn't been used yet. */
    private Optional<Appointment> todaysBooking(long customerId, long branchId, Instant now) {
        LocalDate today = now.atZone(IST).toLocalDate();
        List<Long> ids = jdbc.queryForList("""
                SELECT id FROM appointments WHERE customer_id = :c AND branch_id = :b AND party_size IS NOT NULL
                  AND status IN ('CONFIRMED', 'COMPLETED') AND starts_at >= :from AND starts_at < :until ORDER BY starts_at""",
                new MapSqlParameterSource("c", customerId).addValue("b", branchId)
                        .addValue("from", Timestamp.from(today.atStartOfDay(IST).toInstant()))
                        .addValue("until", Timestamp.from(today.plusDays(1).atStartOfDay(IST).toInstant())), Long.class);
        return ids.stream().map(appointments::findById).flatMap(Optional::stream)
                .filter(b -> usableBooking(b, customerId, branchId, now)).findFirst();
    }

    private boolean usableBooking(Appointment b, long customerId, long branchId, Instant now) {
        if (b.getCustomerId() != customerId || b.getBranchId() != branchId || b.getPartySize() == null
                || (b.getStatus() != Appointment.Status.CONFIRMED && b.getStatus() != Appointment.Status.COMPLETED)
                || !b.getStartsAt().atZone(IST).toLocalDate().equals(now.atZone(IST).toLocalDate())) {
            return false;
        }
        // One bill per booking: a paid or pending bill already used it.
        Integer used = jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE bill_for_appointment_id = :id AND status IN "
                + "('PENDING_PAYMENT', 'CONFIRMED', 'COMPLETED')", Map.of("id", b.getId()), Integer.class);
        return used == null || used == 0;
    }

    private boolean coverPaid(Appointment b) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE appointment_id = :id AND status = 'CAPTURED'",
                Map.of("id", b.getId()), Integer.class);
        return n != null && n > 0;
    }

    // ----- helpers -----

    BigDecimal coverPerGuest(String tier) {
        return settings.decimal("PREMIUM".equals(tier) ? COVER_PREMIUM : COVER_CAFE).setScale(2, RoundingMode.HALF_UP);
    }

    static Meal mealAt(Instant at) {
        return at.atZone(IST).toLocalTime().isBefore(DINNER_FROM) ? Meal.LUNCH : Meal.DINNER;
    }

    List<Offer> offers(long org) {
        return jdbc.query("SELECT * FROM dining_offers WHERE organization_id = :org ORDER BY percent DESC, id",
                Map.of("org", org), (rs, i) -> offer(rs));
    }

    private static Offer offer(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Offer(rs.getLong("id"), rs.getString("title"), rs.getInt("percent"), Meal.valueOf(rs.getString("meal")));
    }

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
            out.add(new TableSlot(t.toString(), start, covers + partySize <= capacity, t.isBefore(DINNER_FROM) ? Meal.LUNCH : Meal.DINNER));
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
