package com.bookeasy.venue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.appointment.Appointment;
import com.bookeasy.appointment.AppointmentView;
import com.bookeasy.appointment.BookingService;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Movies now showing, showtimes per theatre, seat maps and seat bookings. */
@Service
public class CinemaService {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final int MAX_SEATS = 10;
    /** Online booking closes this long before the show starts. */
    static final Duration CLOSES_BEFORE = Duration.ofMinutes(5);
    private static final double MAX_RADIUS_KM = 50;

    private final NamedParameterJdbcTemplate jdbc;
    private final BookingService bookings;
    private final ObjectMapper json;
    private final Clock clock;

    public CinemaService(NamedParameterJdbcTemplate jdbc, BookingService bookings, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.bookings = bookings;
        this.json = json;
        this.clock = clock;
    }

    // ----- models -----

    public record Movie(long id, String title, String languages, String certificate, String genres, int durationMinutes,
                        String synopsis, String cast, LocalDate releaseDate, String posterFrom, String posterTo) {
    }

    public record NowShowing(Movie movie, int theatres, Instant nextShow) {
    }

    public record Show(long id, Instant startsAt, String format, String language, String screenName, int seatsLeft,
                       int totalSeats, BigDecimal fromPrice) {
    }

    public record TheatreShows(long organizationId, String theatreName, String slug, long branchId, String branchName,
                               String locality, String city, double distanceKm, List<Show> shows) {
    }

    public record MoviePage(Movie movie, LocalDate date, List<LocalDate> dates, List<TheatreShows> theatres) {
    }

    public record SeatRow(String row, String tier, int seats, List<Integer> gapsAfter) {
    }

    public record SeatMap(long showtimeId, Instant startsAt, String format, String language, Movie movie,
                          String theatreName, String branchName, String screenName, List<SeatRow> rows,
                          Map<String, BigDecimal> prices, List<String> taken, boolean bookable) {
    }

    // ----- queries -----

    private static final String NEARBY = """
            FROM showtimes st
            JOIN cinema_screens sc ON sc.id = st.screen_id
            JOIN organization_branches b ON b.id = sc.branch_id
            JOIN organizations o ON o.id = b.organization_id
            JOIN movies m ON m.id = st.movie_id
            WHERE m.is_active AND o.verification_status = 'APPROVED' AND b.is_active
              AND st.starts_at > :from AND st.starts_at < :until
              AND ST_Distance_Sphere(b.location, ST_SRID(POINT(:lng, :lat), 4326)) <= :radiusM
            """;

    private MapSqlParameterSource area(double lat, double lng, double radiusKm) {
        return new MapSqlParameterSource().addValue("lat", lat).addValue("lng", lng)
                .addValue("radiusM", Math.min(Math.max(radiusKm, 1), MAX_RADIUS_KM) * 1000);
    }

    @Transactional(readOnly = true)
    public List<NowShowing> nowShowing(double lat, double lng, double radiusKm) {
        Instant now = clock.instant();
        MapSqlParameterSource p = area(lat, lng, radiusKm).addValue("from", Timestamp.from(now.plus(CLOSES_BEFORE)))
                .addValue("until", Timestamp.from(now.plus(Duration.ofDays(8))));
        return jdbc.query("SELECT m.*, COUNT(DISTINCT b.id) AS theatres, MIN(st.starts_at) AS next_show " + NEARBY
                        + " GROUP BY m.id ORDER BY m.release_date DESC, m.title",
                p, (rs, i) -> new NowShowing(movie(rs), rs.getInt("theatres"), rs.getTimestamp("next_show").toInstant()));
    }

    @Transactional(readOnly = true)
    public MoviePage movie(long movieId, LocalDate date, double lat, double lng, double radiusKm) {
        Movie movie = jdbc.query("SELECT * FROM movies WHERE id = :id AND is_active", Map.of("id", movieId),
                (rs, i) -> movie(rs)).stream().findFirst().orElseThrow(() -> ApiException.notFound("Movie"));
        Instant now = clock.instant();
        MapSqlParameterSource week = area(lat, lng, radiusKm).addValue("from", Timestamp.from(now.plus(CLOSES_BEFORE)))
                .addValue("until", Timestamp.from(now.plus(Duration.ofDays(8)))).addValue("movie", movieId);
        List<LocalDate> dates = jdbc.query("SELECT st.starts_at " + NEARBY + " AND m.id = :movie ORDER BY st.starts_at", week,
                        (rs, i) -> rs.getTimestamp(1).toInstant().atZone(IST).toLocalDate())
                .stream().distinct().toList();
        LocalDate day = date != null && dates.contains(date) ? date : dates.isEmpty() ? LocalDate.now(clock.withZone(IST)) : dates.get(0);

        Instant dayStart = day.atStartOfDay(IST).toInstant();
        Instant from = dayStart.isAfter(now.plus(CLOSES_BEFORE)) ? dayStart : now.plus(CLOSES_BEFORE);
        MapSqlParameterSource p = area(lat, lng, radiusKm).addValue("from", Timestamp.from(from))
                .addValue("until", Timestamp.from(day.plusDays(1).atStartOfDay(IST).toInstant())).addValue("movie", movieId);
        Map<Long, TheatreShows> byBranch = new LinkedHashMap<>();
        Map<Long, List<Show>> shows = new LinkedHashMap<>();
        jdbc.query("""
                SELECT st.id, st.starts_at, st.format, st.language, st.prices, sc.name AS screen, sc.layout,
                       (SELECT COUNT(*) FROM ticket_seats ts WHERE ts.showtime_id = st.id) AS sold,
                       b.id AS branch_id, b.name AS branch_name, b.locality, b.city, o.id AS org_id, o.name AS org_name, o.slug,
                       ST_Distance_Sphere(b.location, ST_SRID(POINT(:lng, :lat), 4326)) AS distance_m
                """ + NEARBY + " AND m.id = :movie ORDER BY distance_m, b.id, st.starts_at", p, rs -> {
            long branch = rs.getLong("branch_id");
            byBranch.computeIfAbsent(branch, k -> {
                try {
                    return new TheatreShows(rs.getLong("org_id"), rs.getString("org_name"), rs.getString("slug"), branch,
                            rs.getString("branch_name"), rs.getString("locality"), rs.getString("city"),
                            Math.round(rs.getDouble("distance_m") / 100.0) / 10.0, null);
                } catch (java.sql.SQLException e) {
                    throw new IllegalStateException(e);
                }
            });
            int total = totalSeats(rows(rs.getString("layout")));
            Map<String, BigDecimal> prices = prices(rs.getString("prices"));
            shows.computeIfAbsent(branch, k -> new ArrayList<>()).add(new Show(rs.getLong("id"),
                    rs.getTimestamp("starts_at").toInstant(), rs.getString("format"), rs.getString("language"),
                    rs.getString("screen"), Math.max(0, total - rs.getInt("sold")), total,
                    prices.values().stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO)));
        });
        List<TheatreShows> theatres = byBranch.values().stream()
                .map(t -> new TheatreShows(t.organizationId(), t.theatreName(), t.slug(), t.branchId(), t.branchName(),
                        t.locality(), t.city(), t.distanceKm(), shows.getOrDefault(t.branchId(), List.of())))
                .toList();
        return new MoviePage(movie, day, dates, theatres);
    }

    public record TheatreShow(long id, Instant startsAt, String format, String language, String screen, long movieId,
                              String title, String certificate) {
    }

    /** Movies and today's/upcoming shows at one theatre (for its business page). */
    @Transactional(readOnly = true)
    public List<TheatreShow> theatreSchedule(long organizationId) {
        Instant now = clock.instant();
        return jdbc.query("""
                SELECT st.id, st.starts_at, st.format, st.language, sc.name AS screen, m.id AS movie_id, m.title, m.certificate
                FROM showtimes st JOIN cinema_screens sc ON sc.id = st.screen_id
                JOIN organization_branches b ON b.id = sc.branch_id JOIN movies m ON m.id = st.movie_id
                JOIN organizations o ON o.id = b.organization_id
                WHERE b.organization_id = :org AND o.verification_status = 'APPROVED' AND b.is_active AND m.is_active
                  AND st.starts_at > :from AND st.starts_at < :until
                ORDER BY m.title, st.starts_at""", new MapSqlParameterSource("org", organizationId)
                .addValue("from", Timestamp.from(now.plus(CLOSES_BEFORE)))
                .addValue("until", Timestamp.from(now.plus(Duration.ofDays(2)))),
                (rs, i) -> new TheatreShow(rs.getLong("id"), rs.getTimestamp("starts_at").toInstant(), rs.getString("format"),
                        rs.getString("language"), rs.getString("screen"), rs.getLong("movie_id"), rs.getString("title"),
                        rs.getString("certificate")));
    }

    @Transactional(readOnly = true)
    public SeatMap seatMap(long showtimeId) {
        Map<String, Object> s = show(showtimeId, false);
        Movie movie = jdbc.query("SELECT * FROM movies WHERE id = :id", Map.of("id", s.get("movie_id")),
                (rs, i) -> movie(rs)).get(0);
        List<String> taken = jdbc.queryForList("SELECT seat_label FROM ticket_seats WHERE showtime_id = :id",
                Map.of("id", showtimeId), String.class);
        Instant startsAt = utc(s.get("starts_at"));
        return new SeatMap(showtimeId, startsAt, (String) s.get("format"), (String) s.get("language"), movie,
                (String) s.get("org_name"), (String) s.get("branch_name"), (String) s.get("screen"),
                rows((String) s.get("layout")), prices((String) s.get("prices")), taken,
                startsAt.isAfter(clock.instant().plus(CLOSES_BEFORE)));
    }

    // ----- booking -----

    @Transactional
    public AppointmentView book(long customerId, long showtimeId, List<String> requested) {
        Set<String> seats = new LinkedHashSet<>();
        for (String r : requested == null ? List.<String>of() : requested) {
            seats.add(r == null ? "" : r.trim().toUpperCase());
        }
        if (seats.isEmpty() || seats.size() > MAX_SEATS || seats.size() != requested.size()) {
            throw ApiException.invalidField("seats", "INVALID", "Choose between 1 and " + MAX_SEATS + " different seats");
        }
        Map<String, Object> s = show(showtimeId, true);
        Instant startsAt = utc(s.get("starts_at"));
        if (!startsAt.isAfter(clock.instant().plus(CLOSES_BEFORE))) {
            throw new ApiException(ErrorCode.SHOW_CLOSED);
        }
        Map<String, String> tierOf = new LinkedHashMap<>();
        for (SeatRow row : rows((String) s.get("layout"))) {
            for (int n = 1; n <= row.seats(); n++) {
                tierOf.put(row.row() + n, row.tier());
            }
        }
        Map<String, BigDecimal> prices = prices((String) s.get("prices"));
        BigDecimal total = BigDecimal.ZERO;
        List<Object[]> lines = new ArrayList<>();
        for (String seat : seats) {
            String tier = tierOf.get(seat);
            BigDecimal price = tier == null ? null : prices.get(tier);
            if (price == null) {
                throw ApiException.invalidField("seats", "UNKNOWN_SEAT", "Seat " + seat + " doesn't exist");
            }
            total = total.add(price);
            lines.add(new Object[]{seat, tier, price});
        }
        int duration = ((Number) s.get("duration_minutes")).intValue();
        // The seat numbers stay in the title after a cancelled booking's seats go back on sale.
        String seatList = " · " + String.join(", ", seats);
        String movieTitle = s.get("title") + " (" + s.get("format") + ")";
        String title = movieTitle.length() + seatList.length() > 150
                ? movieTitle.substring(0, 150 - seatList.length()) + seatList : movieTitle + seatList;
        Appointment a = bookings.createVenueBooking(new BookingService.VenueBooking(customerId,
                ((Number) s.get("org_id")).longValue(), ((Number) s.get("branch_id")).longValue(),
                ((Number) s.get("booking_service_id")).longValue(), startsAt, startsAt.plus(Duration.ofMinutes(duration + 15)),
                title, total.setScale(2, RoundingMode.HALF_UP), null, showtimeId, null));
        try {
            for (Object[] l : lines) {
                jdbc.update("INSERT INTO ticket_seats (showtime_id, seat_label, appointment_id, tier, price) "
                                + "VALUES (:show, :seat, :appt, :tier, :price)",
                        new MapSqlParameterSource("show", showtimeId).addValue("seat", l[0]).addValue("appt", a.getId())
                                .addValue("tier", l[1]).addValue("price", l[2]));
            }
        } catch (DuplicateKeyException e) {
            throw new ApiException(ErrorCode.SEATS_UNAVAILABLE);
        }
        return bookings.finishVenueBooking(a);
    }

    // ----- helpers -----

    private Map<String, Object> show(long showtimeId, boolean forUpdate) {
        List<Map<String, Object>> found = jdbc.queryForList("""
                SELECT st.starts_at, st.format, st.language, st.prices, st.movie_id, sc.name AS screen, sc.layout,
                       b.id AS branch_id, b.name AS branch_name, o.id AS org_id, o.name AS org_name,
                       m.title, m.duration_minutes, vp.booking_service_id
                FROM showtimes st JOIN cinema_screens sc ON sc.id = st.screen_id
                JOIN organization_branches b ON b.id = sc.branch_id JOIN organizations o ON o.id = b.organization_id
                JOIN movies m ON m.id = st.movie_id
                JOIN venue_profiles vp ON vp.organization_id = o.id AND vp.kind = 'CINEMA'
                WHERE st.id = :id AND o.verification_status = 'APPROVED' AND b.is_active AND m.is_active"""
                + (forUpdate ? " FOR UPDATE OF st" : ""), Map.of("id", showtimeId));
        if (found.isEmpty()) {
            throw ApiException.notFound("Show");
        }
        return found.get(0);
    }

    List<SeatRow> rows(String layout) {
        try {
            List<Map<String, Object>> raw = json.readValue(layout, new TypeReference<>() { });
            List<SeatRow> out = new ArrayList<>();
            for (Map<String, Object> r : raw) {
                @SuppressWarnings("unchecked")
                List<Integer> gaps = r.get("gapsAfter") == null ? List.of() : (List<Integer>) r.get("gapsAfter");
                out.add(new SeatRow((String) r.get("row"), (String) r.get("tier"), ((Number) r.get("seats")).intValue(), gaps));
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("Bad screen layout", e);
        }
    }

    private Map<String, BigDecimal> prices(String text) {
        try {
            Map<String, BigDecimal> raw = json.readValue(text, new TypeReference<LinkedHashMap<String, BigDecimal>>() { });
            Map<String, BigDecimal> out = new LinkedHashMap<>();
            raw.forEach((k, v) -> out.put(k, v.setScale(2, RoundingMode.HALF_UP)));
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("Bad showtime prices", e);
        }
    }

    /** DATETIME columns hold UTC; the driver returns them as LocalDateTime from queryForList. */
    static Instant utc(Object value) {
        if (value instanceof Timestamp t) {
            return t.toInstant();
        }
        return ((java.time.LocalDateTime) value).toInstant(java.time.ZoneOffset.UTC);
    }

    private static int totalSeats(List<SeatRow> rows) {
        return rows.stream().mapToInt(SeatRow::seats).sum();
    }

    private static Movie movie(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Movie(rs.getLong("id"), rs.getString("title"), rs.getString("languages"), rs.getString("certificate"),
                rs.getString("genres"), rs.getInt("duration_minutes"), rs.getString("synopsis"), rs.getString("cast_names"),
                rs.getDate("release_date").toLocalDate(), rs.getString("poster_from"), rs.getString("poster_to"));
    }
}
