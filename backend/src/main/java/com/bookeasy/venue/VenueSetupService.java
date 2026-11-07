package com.bookeasy.venue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.bookeasy.organization.entity.Organization;
import com.bookeasy.organization.service.OrganizationAccess;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The owner's side of cinemas and restaurants (venue settings, menu, screens, showtimes) and the admin's movie
 * catalogue. Theatres pick movies from the catalogue; they can't invent titles.
 */
@Service
public class VenueSetupService {

    static final Set<String> FORMATS = Set.of("2D", "3D", "IMAX", "4DX");
    static final Set<String> CERTIFICATES = Set.of("U", "UA", "A");
    private static final Pattern ROW = Pattern.compile("[A-Z]{1,2}");
    private static final Pattern TIER = Pattern.compile("[A-Z][A-Z_]{1,15}");
    private static final Pattern COLOUR = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final int MAX_SEATS_PER_ROW = 40;
    private static final int MAX_ROWS = 30;
    /** Cleaning and ads between two shows on the same screen. */
    static final Duration TURNAROUND = Duration.ofMinutes(15);

    private final NamedParameterJdbcTemplate jdbc;
    private final OrganizationAccess access;
    private final CinemaService cinema;
    private final ObjectMapper json;
    private final Clock clock;
    private final com.bookeasy.file.FileService fileService;
    private final com.bookeasy.file.UploadedFileRepository files;

    static final com.bookeasy.file.FileService.UploadRules POSTER_RULES = new com.bookeasy.file.FileService.UploadRules(
            Set.of(com.bookeasy.file.ContentSniffer.PNG, com.bookeasy.file.ContentSniffer.JPEG, com.bookeasy.file.ContentSniffer.WEBP),
            5L * 1024 * 1024);

    public VenueSetupService(NamedParameterJdbcTemplate jdbc, OrganizationAccess access, CinemaService cinema,
                             ObjectMapper json, Clock clock, com.bookeasy.file.FileService fileService,
                             com.bookeasy.file.UploadedFileRepository files) {
        this.fileService = fileService;
        this.files = files;
        this.jdbc = jdbc;
        this.access = access;
        this.cinema = cinema;
        this.json = json;
        this.clock = clock;
    }

    // ----- models -----

    public record ProfileRequest(String cuisines, BigDecimal costForTwo, Boolean vegOnly, Integer capacityPerSlot,
                                 String offerText, String features, String diningTier) {
    }

    /** diningTier: CAFE (₹10 cover per guest) or PREMIUM (₹25); see platform settings. */
    public record Profile(String cuisines, BigDecimal costForTwo, boolean vegOnly, Integer capacityPerSlot, String offerText,
                          String features, String diningTier) {
    }

    public record OfferRequest(String title, Integer percent, String meal) {
    }

    public record MenuRequest(String section, String name, String description, BigDecimal price, Boolean veg,
                              Boolean bestseller) {
    }

    public record MenuLine(long id, String section, String name, String description, BigDecimal price, boolean veg,
                           boolean bestseller) {
    }

    public record ScreenRequest(Long branchId, String name, List<CinemaService.SeatRow> rows) {
    }

    public record Screen(long id, long branchId, String branchName, String name, List<CinemaService.SeatRow> rows,
                         int totalSeats, List<String> tiers) {
    }

    public record ShowtimeRequest(Long screenId, Long movieId, Instant startsAt, String format, String language,
                                  Map<String, BigDecimal> prices) {
    }

    public record ShowLine(long id, long screenId, String screenName, long movieId, String movieTitle, Instant startsAt,
                           Instant endsAt, String format, String language, Map<String, BigDecimal> prices, int sold,
                           int totalSeats) {
    }

    /** kind is null when the business's category isn't a cinema or restaurant one. */
    public record VenueSetup(String kind, Profile profile, List<MenuLine> menu, List<Screen> screens, List<ShowLine> shows,
                             List<DiningService.Offer> offers) {
    }

    public record MovieRequest(String title, String languages, String certificate, String genres, Integer durationMinutes,
                               String synopsis, String cast, LocalDate releaseDate, String posterFrom, String posterTo) {
    }

    public record CatalogMovie(long id, String title, String languages, String certificate, String genres,
                               int durationMinutes, String synopsis, String cast, LocalDate releaseDate, String posterFrom,
                               String posterTo, boolean active, String posterUrl) {
    }

    // ----- owner: overview and profile -----

    @Transactional(readOnly = true)
    public VenueSetup setup(long userId, long orgId) {
        Organization org = access.owned(userId, orgId);
        String kind = kindOf(org);
        if (kind == null) {
            return new VenueSetup(null, null, List.of(), List.of(), List.of(), List.of());
        }
        Profile profile = jdbc.query("SELECT * FROM venue_profiles WHERE organization_id = :org", Map.of("org", orgId),
                (rs, i) -> new Profile(rs.getString("cuisines"), rs.getBigDecimal("cost_for_two"), rs.getBoolean("veg_only"),
                        (Integer) rs.getObject("capacity_per_slot", Integer.class), rs.getString("offer_text"),
                        rs.getString("features"), rs.getString("dining_tier"))).stream().findFirst().orElse(null);
        return new VenueSetup(kind, profile, kind.equals("DINING") ? menu(orgId) : List.of(),
                kind.equals("CINEMA") ? screens(orgId) : List.of(), kind.equals("CINEMA") ? shows(orgId) : List.of(),
                kind.equals("DINING") ? offerList(orgId) : List.of());
    }

    @Transactional
    public VenueSetup saveProfile(long userId, long orgId, ProfileRequest req) {
        Organization org = access.ownedForUpdate(userId, orgId);
        String kind = requireKind(org, null);
        Integer capacity = null;
        String cuisines = null;
        BigDecimal cost = null;
        if (kind.equals("DINING")) {
            capacity = req.capacityPerSlot();
            if (capacity == null || capacity < 1 || capacity > 2000) {
                throw ApiException.invalidField("capacityPerSlot", "INVALID", "Enter how many guests you can seat at once (1–2000)");
            }
            cuisines = text("cuisines", req.cuisines(), 200, true);
            cost = req.costForTwo() == null ? null : money("costForTwo", req.costForTwo());
        }
        String tier = req.diningTier() == null || req.diningTier().isBlank() ? "CAFE" : req.diningTier().trim().toUpperCase();
        if (!tier.equals("CAFE") && !tier.equals("PREMIUM")) {
            throw ApiException.invalidField("diningTier", "INVALID", "Choose café or premium");
        }
        String offer = text("offerText", req.offerText(), 200, false);
        String features = text("features", req.features(), 300, false);
        boolean veg = Boolean.TRUE.equals(req.vegOnly());
        MapSqlParameterSource p = new MapSqlParameterSource("org", orgId).addValue("kind", kind).addValue("cuisines", cuisines)
                .addValue("cost", cost).addValue("veg", veg).addValue("cap", capacity).addValue("offer", offer)
                .addValue("features", features).addValue("tier", tier);
        int updated = jdbc.update("""
                UPDATE venue_profiles SET cuisines = :cuisines, cost_for_two = :cost, veg_only = :veg,
                       capacity_per_slot = :cap, offer_text = :offer, features = :features, dining_tier = :tier
                WHERE organization_id = :org""", p);
        if (updated == 0) {
            // Customer bookings point at this hidden service so they fit the appointments table.
            Instant now = clock.instant();
            GeneratedKeyHolder key = new GeneratedKeyHolder();
            jdbc.update("""
                    INSERT INTO services (organization_id, name, duration_minutes, price, requires_staff, slot_interval_minutes,
                                          min_notice_minutes, is_active, created_at, updated_at)
                    VALUES (:org, :name, :minutes, 0, FALSE, 30, 0, FALSE, :now, :now)""",
                    new MapSqlParameterSource("org", orgId).addValue("name", kind.equals("CINEMA") ? "Movie tickets" : "Table booking")
                            .addValue("minutes", kind.equals("CINEMA") ? 180 : 90).addValue("now", Timestamp.from(now)),
                    key, new String[]{"id"});
            p.addValue("service", key.getKey().longValue());
            jdbc.update("""
                    INSERT INTO venue_profiles (organization_id, kind, booking_service_id, cuisines, cost_for_two, veg_only,
                                                capacity_per_slot, offer_text, features, dining_tier)
                    VALUES (:org, :kind, :service, :cuisines, :cost, :veg, :cap, :offer, :features, :tier)""", p);
        }
        return setup(userId, orgId);
    }

    // ----- owner: offers -----

    @Transactional
    public VenueSetup addOffer(long userId, long orgId, OfferRequest req) {
        requireKind(access.ownedForUpdate(userId, orgId), "DINING");
        requireProfile(orgId);
        if (req.percent() == null || req.percent() < 1 || req.percent() > 60) {
            throw ApiException.invalidField("percent", "INVALID", "A discount is 1% to 60%");
        }
        String meal = req.meal() == null || req.meal().isBlank() ? "ALL_DAY" : req.meal().trim().toUpperCase();
        if (!List.of("LUNCH", "DINNER", "ALL_DAY").contains(meal)) {
            throw ApiException.invalidField("meal", "INVALID", "Choose lunch, dinner or all day");
        }
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dining_offers WHERE organization_id = :org", Map.of("org", orgId),
                Integer.class);
        if (count != null && count >= 10) {
            throw ApiException.invalidField("title", "TOO_MANY", "A restaurant can have up to 10 offers");
        }
        String title = req.title() == null || req.title().isBlank() ? "Flat " + req.percent() + "% off" : text("title", req.title(), 80, true);
        jdbc.update("INSERT INTO dining_offers (organization_id, title, percent, meal, created_at) VALUES (:org, :t, :p, :m, :now)",
                new MapSqlParameterSource("org", orgId).addValue("t", title).addValue("p", req.percent()).addValue("m", meal)
                        .addValue("now", Timestamp.from(clock.instant())));
        return setup(userId, orgId);
    }

    @Transactional
    public VenueSetup deleteOffer(long userId, long orgId, long offerId) {
        access.ownedForUpdate(userId, orgId);
        if (jdbc.update("DELETE FROM dining_offers WHERE id = :id AND organization_id = :org",
                new MapSqlParameterSource("id", offerId).addValue("org", orgId)) == 0) {
            throw ApiException.notFound("Offer");
        }
        return setup(userId, orgId);
    }

    private List<DiningService.Offer> offerList(long orgId) {
        return jdbc.query("SELECT * FROM dining_offers WHERE organization_id = :org ORDER BY percent DESC, id", Map.of("org", orgId),
                (rs, i) -> new DiningService.Offer(rs.getLong("id"), rs.getString("title"), rs.getInt("percent"),
                        DiningService.Meal.valueOf(rs.getString("meal"))));
    }

    // ----- owner: menu -----

    @Transactional
    public VenueSetup addMenuItem(long userId, long orgId, MenuRequest req) {
        requireKind(access.ownedForUpdate(userId, orgId), "DINING");
        requireProfile(orgId);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM menu_items WHERE organization_id = :org", Map.of("org", orgId),
                Integer.class);
        if (count != null && count >= 300) {
            throw ApiException.invalidField("name", "TOO_MANY", "A menu can have up to 300 dishes");
        }
        jdbc.update("""
                INSERT INTO menu_items (organization_id, section, name, description, price, is_veg, is_bestseller, sort_order)
                VALUES (:org, :section, :name, :description, :price, :veg, :best, :sort)""",
                new MapSqlParameterSource("org", orgId).addValue("section", text("section", req.section(), 60, true))
                        .addValue("name", text("name", req.name(), 120, true))
                        .addValue("description", text("description", req.description(), 300, false))
                        .addValue("price", money("price", req.price())).addValue("veg", !Boolean.FALSE.equals(req.veg()))
                        .addValue("best", Boolean.TRUE.equals(req.bestseller())).addValue("sort", (count == null ? 0 : count) + 1));
        return setup(userId, orgId);
    }

    @Transactional
    public VenueSetup deleteMenuItem(long userId, long orgId, long itemId) {
        access.ownedForUpdate(userId, orgId);
        if (jdbc.update("DELETE FROM menu_items WHERE id = :id AND organization_id = :org",
                new MapSqlParameterSource("id", itemId).addValue("org", orgId)) == 0) {
            throw ApiException.notFound("Dish");
        }
        return setup(userId, orgId);
    }

    // ----- owner: screens -----

    @Transactional
    public VenueSetup addScreen(long userId, long orgId, ScreenRequest req) {
        requireKind(access.ownedForUpdate(userId, orgId), "CINEMA");
        requireProfile(orgId);
        if (req.branchId() == null || jdbc.queryForObject(
                "SELECT COUNT(*) FROM organization_branches WHERE id = :b AND organization_id = :org",
                new MapSqlParameterSource("b", req.branchId()).addValue("org", orgId), Integer.class) == 0) {
            throw ApiException.invalidField("branchId", "INVALID", "Choose one of your branches");
        }
        String name = text("name", req.name(), 40, true);
        List<CinemaService.SeatRow> rows = validRows(req.rows());
        try {
            jdbc.update("INSERT INTO cinema_screens (branch_id, name, layout, created_at) VALUES (:b, :name, :layout, :now)",
                    new MapSqlParameterSource("b", req.branchId()).addValue("name", name)
                            .addValue("layout", json.writeValueAsString(rows)).addValue("now", Timestamp.from(clock.instant())));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return setup(userId, orgId);
    }

    @Transactional
    public VenueSetup deleteScreen(long userId, long orgId, long screenId) {
        access.ownedForUpdate(userId, orgId);
        requireScreen(orgId, screenId);
        if (soldUpcoming("st.screen_id = :id", screenId)) {
            throw new ApiException(ErrorCode.BOOKING_NOT_CHANGEABLE, "Tickets are sold for upcoming shows on this screen");
        }
        jdbc.update("DELETE FROM cinema_screens WHERE id = :id", Map.of("id", screenId));
        return setup(userId, orgId);
    }

    // ----- owner: showtimes -----

    @Transactional
    public VenueSetup addShowtime(long userId, long orgId, ShowtimeRequest req) {
        requireKind(access.ownedForUpdate(userId, orgId), "CINEMA");
        if (req.screenId() == null) {
            throw ApiException.invalidField("screenId", "REQUIRED", "Choose a screen");
        }
        List<CinemaService.SeatRow> rows = requireScreen(orgId, req.screenId());
        if (req.movieId() == null) {
            throw ApiException.invalidField("movieId", "REQUIRED", "Choose a movie");
        }
        Integer duration = jdbc.query("SELECT duration_minutes FROM movies WHERE id = :id AND is_active",
                Map.of("id", req.movieId()), (rs, i) -> rs.getInt(1)).stream().findFirst()
                .orElseThrow(() -> ApiException.invalidField("movieId", "INVALID", "Choose a movie from the list"));
        Instant now = clock.instant();
        if (req.startsAt() == null || !req.startsAt().isAfter(now.plus(Duration.ofMinutes(30)))
                || req.startsAt().isAfter(now.plus(Duration.ofDays(30)))) {
            throw ApiException.invalidField("startsAt", "INVALID", "Shows can be added from 30 minutes to 30 days ahead");
        }
        String format = req.format() == null ? "2D" : req.format().trim().toUpperCase();
        if (!FORMATS.contains(format)) {
            throw ApiException.invalidField("format", "INVALID", "Format must be 2D, 3D, IMAX or 4DX");
        }
        String language = text("language", req.language(), 40, true);
        Map<String, BigDecimal> prices = new LinkedHashMap<>();
        for (CinemaService.SeatRow r : rows) {
            BigDecimal price = req.prices() == null ? null : req.prices().get(r.tier());
            if (price == null) {
                throw ApiException.invalidField("prices", "MISSING", "Set a price for " + r.tier());
            }
            prices.put(r.tier(), money("prices", price));
        }
        Instant ends = req.startsAt().plus(Duration.ofMinutes(duration)).plus(TURNAROUND);
        // Another show on the same screen may not overlap this one (each includes its turnaround).
        Integer clash = jdbc.queryForObject("""
                SELECT COUNT(*) FROM showtimes st JOIN movies m ON m.id = st.movie_id
                WHERE st.screen_id = :screen AND st.starts_at < :ends
                  AND DATE_ADD(st.starts_at, INTERVAL (m.duration_minutes + :turn) MINUTE) > :starts""",
                new MapSqlParameterSource("screen", req.screenId()).addValue("ends", Timestamp.from(ends))
                        .addValue("starts", Timestamp.from(req.startsAt())).addValue("turn", TURNAROUND.toMinutes()), Integer.class);
        if (clash != null && clash > 0) {
            throw ApiException.invalidField("startsAt", "OVERLAP", "Another show is on this screen at that time");
        }
        try {
            jdbc.update("""
                    INSERT INTO showtimes (screen_id, movie_id, starts_at, format, language, prices, created_at)
                    VALUES (:screen, :movie, :starts, :format, :language, :prices, :now)""",
                    new MapSqlParameterSource("screen", req.screenId()).addValue("movie", req.movieId())
                            .addValue("starts", Timestamp.from(req.startsAt())).addValue("format", format)
                            .addValue("language", language).addValue("prices", json.writeValueAsString(prices))
                            .addValue("now", Timestamp.from(now)));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return setup(userId, orgId);
    }

    @Transactional
    public VenueSetup deleteShowtime(long userId, long orgId, long showtimeId) {
        access.ownedForUpdate(userId, orgId);
        Integer mine = jdbc.queryForObject("""
                SELECT COUNT(*) FROM showtimes st JOIN cinema_screens sc ON sc.id = st.screen_id
                JOIN organization_branches b ON b.id = sc.branch_id WHERE st.id = :id AND b.organization_id = :org""",
                new MapSqlParameterSource("id", showtimeId).addValue("org", orgId), Integer.class);
        if (mine == null || mine == 0) {
            throw ApiException.notFound("Show");
        }
        Integer sold = jdbc.queryForObject("SELECT COUNT(*) FROM ticket_seats WHERE showtime_id = :id", Map.of("id", showtimeId),
                Integer.class);
        if (sold != null && sold > 0) {
            throw new ApiException(ErrorCode.BOOKING_NOT_CHANGEABLE, "Tickets are already sold for this show");
        }
        jdbc.update("DELETE FROM showtimes WHERE id = :id", Map.of("id", showtimeId));
        return setup(userId, orgId);
    }

    // ----- movie catalogue -----

    @Transactional(readOnly = true)
    public List<CatalogMovie> catalog(boolean activeOnly) {
        return jdbc.query("SELECT m.*, " + CinemaService.POSTER + " FROM movies m" + (activeOnly ? " WHERE m.is_active" : "")
                + " ORDER BY m.is_active DESC, m.release_date DESC, m.title", Map.of(), (rs, i) -> new CatalogMovie(rs.getLong("id"),
                rs.getString("title"), rs.getString("languages"), rs.getString("certificate"), rs.getString("genres"),
                rs.getInt("duration_minutes"), rs.getString("synopsis"), rs.getString("cast_names"),
                rs.getDate("release_date").toLocalDate(), rs.getString("poster_from"), rs.getString("poster_to"),
                rs.getBoolean("is_active"), CinemaService.posterUrl(rs.getString("poster_public_id"))));
    }

    @Transactional
    public CatalogMovie addMovie(MovieRequest req) {
        String certificate = req.certificate() == null ? "" : req.certificate().trim().toUpperCase();
        if (!CERTIFICATES.contains(certificate)) {
            throw ApiException.invalidField("certificate", "INVALID", "Certificate must be U, UA or A");
        }
        if (req.durationMinutes() == null || req.durationMinutes() < 30 || req.durationMinutes() > 300) {
            throw ApiException.invalidField("durationMinutes", "INVALID", "Running time must be 30–300 minutes");
        }
        if (req.releaseDate() == null) {
            throw ApiException.invalidField("releaseDate", "REQUIRED", "Enter the release date");
        }
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO movies (title, languages, certificate, genres, duration_minutes, synopsis, cast_names, release_date,
                                    poster_from, poster_to, is_active, created_at)
                VALUES (:title, :languages, :cert, :genres, :minutes, :synopsis, :cast, :release, :from, :to, TRUE, :now)""",
                new MapSqlParameterSource("title", text("title", req.title(), 150, true))
                        .addValue("languages", text("languages", req.languages(), 120, true)).addValue("cert", certificate)
                        .addValue("genres", text("genres", req.genres(), 120, true)).addValue("minutes", req.durationMinutes())
                        .addValue("synopsis", text("synopsis", req.synopsis(), 1000, false))
                        .addValue("cast", text("cast", req.cast(), 300, false))
                        .addValue("release", java.sql.Date.valueOf(req.releaseDate()))
                        .addValue("from", colour("posterFrom", req.posterFrom(), "#1d4ed8"))
                        .addValue("to", colour("posterTo", req.posterTo(), "#0f172a"))
                        .addValue("now", Timestamp.from(clock.instant())), key, new String[]{"id"});
        long id = key.getKey().longValue();
        return catalog(false).stream().filter(m -> m.id() == id).findFirst().orElseThrow();
    }

    @Transactional
    public CatalogMovie setMovieActive(long movieId, boolean active) {
        if (jdbc.update("UPDATE movies SET is_active = :a WHERE id = :id",
                new MapSqlParameterSource("a", active).addValue("id", movieId)) == 0) {
            throw ApiException.notFound("Movie");
        }
        return catalog(false).stream().filter(m -> m.id() == movieId).findFirst().orElseThrow();
    }

    /** Replaces a movie's poster photo (admin). */
    @Transactional
    public CatalogMovie setPoster(long adminId, long movieId, org.springframework.web.multipart.MultipartFile upload) {
        Long previous = jdbc.query("SELECT poster_file_id FROM movies WHERE id = :id FOR UPDATE", Map.of("id", movieId),
                (rs, i) -> java.util.Optional.ofNullable(rs.getObject(1, Long.class))).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Movie")).orElse(null);
        com.bookeasy.file.UploadedFile stored = fileService.store(upload, POSTER_RULES, com.bookeasy.file.FileVisibility.PUBLIC,
                com.bookeasy.file.FilePurpose.MOVIE_POSTER, adminId);
        jdbc.update("UPDATE movies SET poster_file_id = :f WHERE id = :id",
                new MapSqlParameterSource("f", stored.getId()).addValue("id", movieId));
        if (previous != null) {
            files.findById(previous).ifPresent(fileService::delete);
        }
        return catalog(false).stream().filter(m -> m.id() == movieId).findFirst().orElseThrow();
    }

    // ----- helpers -----

    /** CINEMA, DINING or null, from the business's category. */
    String kindOf(Organization org) {
        return jdbc.query("""
                SELECT c.booking_workflow FROM subcategories s JOIN categories c ON c.id = s.category_id WHERE s.id = :s""",
                Map.of("s", org.getSubcategoryId()), (rs, i) -> rs.getString(1)).stream()
                .filter(w -> w.equals("CINEMA") || w.equals("DINING")).findFirst().orElse(null);
    }

    private String requireKind(Organization org, String expected) {
        String kind = kindOf(org);
        if (kind == null || (expected != null && !expected.equals(kind))) {
            throw ApiException.notFound(expected == null || expected.equals("CINEMA") ? "Cinema" : "Restaurant");
        }
        return kind;
    }

    private void requireProfile(long orgId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM venue_profiles WHERE organization_id = :org", Map.of("org", orgId),
                Integer.class);
        if (n == null || n == 0) {
            throw ApiException.invalidField("profile", "REQUIRED", "Save the venue settings first");
        }
    }

    private List<CinemaService.SeatRow> requireScreen(long orgId, long screenId) {
        return jdbc.query("""
                SELECT sc.layout FROM cinema_screens sc JOIN organization_branches b ON b.id = sc.branch_id
                WHERE sc.id = :id AND b.organization_id = :org""",
                new MapSqlParameterSource("id", screenId).addValue("org", orgId), (rs, i) -> cinema.rows(rs.getString(1)))
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Screen"));
    }

    private boolean soldUpcoming(String where, long id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM ticket_seats ts JOIN showtimes st ON st.id = ts.showtime_id WHERE "
                        + where + " AND st.starts_at > :now",
                new MapSqlParameterSource("id", id).addValue("now", Timestamp.from(clock.instant())), Integer.class);
        return n != null && n > 0;
    }

    private List<CinemaService.SeatRow> validRows(List<CinemaService.SeatRow> raw) {
        if (raw == null || raw.isEmpty() || raw.size() > MAX_ROWS) {
            throw ApiException.invalidField("rows", "INVALID", "A screen needs 1 to " + MAX_ROWS + " rows");
        }
        Set<String> seen = new HashSet<>();
        List<CinemaService.SeatRow> out = new ArrayList<>();
        for (CinemaService.SeatRow r : raw) {
            String row = r.row() == null ? "" : r.row().trim().toUpperCase();
            String tier = r.tier() == null ? "" : r.tier().trim().toUpperCase();
            if (!ROW.matcher(row).matches() || !seen.add(row)) {
                throw ApiException.invalidField("rows", "INVALID_ROW", "Rows are named A, B, C… and each name is used once");
            }
            if (!TIER.matcher(tier).matches()) {
                throw ApiException.invalidField("rows", "INVALID_TIER", "Seat classes use capital letters, e.g. CLASSIC");
            }
            if (r.seats() < 1 || r.seats() > MAX_SEATS_PER_ROW) {
                throw ApiException.invalidField("rows", "INVALID_SEATS", "A row has 1 to " + MAX_SEATS_PER_ROW + " seats");
            }
            List<Integer> gaps = r.gapsAfter() == null ? List.of()
                    : r.gapsAfter().stream().filter(g -> g != null && g > 0 && g < r.seats()).distinct().sorted().toList();
            out.add(new CinemaService.SeatRow(row, tier, r.seats(), gaps));
        }
        return out;
    }

    private List<MenuLine> menu(long orgId) {
        return jdbc.query("SELECT * FROM menu_items WHERE organization_id = :org ORDER BY sort_order, id", Map.of("org", orgId),
                (rs, i) -> new MenuLine(rs.getLong("id"), rs.getString("section"), rs.getString("name"),
                        rs.getString("description"), rs.getBigDecimal("price"), rs.getBoolean("is_veg"),
                        rs.getBoolean("is_bestseller")));
    }

    private List<Screen> screens(long orgId) {
        return jdbc.query("""
                SELECT sc.id, sc.branch_id, b.name AS branch_name, sc.name, sc.layout FROM cinema_screens sc
                JOIN organization_branches b ON b.id = sc.branch_id WHERE b.organization_id = :org ORDER BY b.id, sc.name""",
                Map.of("org", orgId), (rs, i) -> {
                    List<CinemaService.SeatRow> rows = cinema.rows(rs.getString("layout"));
                    return new Screen(rs.getLong("id"), rs.getLong("branch_id"), rs.getString("branch_name"), rs.getString("name"),
                            rows, rows.stream().mapToInt(CinemaService.SeatRow::seats).sum(),
                            rows.stream().map(CinemaService.SeatRow::tier).distinct().toList());
                });
    }

    private List<ShowLine> shows(long orgId) {
        Map<Long, Integer> totals = new LinkedHashMap<>();
        screens(orgId).forEach(s -> totals.put(s.id(), s.totalSeats()));
        Instant now = clock.instant();
        return jdbc.query("""
                SELECT st.id, st.screen_id, sc.name AS screen, m.id AS movie_id, m.title, m.duration_minutes, st.starts_at,
                       st.format, st.language, st.prices, (SELECT COUNT(*) FROM ticket_seats ts WHERE ts.showtime_id = st.id) AS sold
                FROM showtimes st JOIN cinema_screens sc ON sc.id = st.screen_id
                JOIN organization_branches b ON b.id = sc.branch_id JOIN movies m ON m.id = st.movie_id
                WHERE b.organization_id = :org AND st.starts_at > :from ORDER BY st.starts_at, sc.name LIMIT 300""",
                new MapSqlParameterSource("org", orgId).addValue("from", Timestamp.from(now.minus(Duration.ofHours(3)))),
                (rs, i) -> {
                    Instant starts = rs.getTimestamp("starts_at").toInstant();
                    Map<String, BigDecimal> prices = new LinkedHashMap<>();
                    try {
                        json.readValue(rs.getString("prices"), new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, BigDecimal>>() { })
                                .forEach((k, v) -> prices.put(k, v.setScale(2, RoundingMode.HALF_UP)));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                    return new ShowLine(rs.getLong("id"), rs.getLong("screen_id"), rs.getString("screen"), rs.getLong("movie_id"),
                            rs.getString("title"), starts, starts.plus(Duration.ofMinutes(rs.getInt("duration_minutes"))),
                            rs.getString("format"), rs.getString("language"), prices, rs.getInt("sold"),
                            totals.getOrDefault(rs.getLong("screen_id"), 0));
                });
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

    private static BigDecimal money(String field, BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100_000)) > 0) {
            throw ApiException.invalidField(field, "INVALID", "Enter an amount from ₹0 to ₹1,00,000");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String colour(String field, String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        if (!COLOUR.matcher(value.trim()).matches()) {
            throw ApiException.invalidField(field, "INVALID", "Use a colour like #1d4ed8");
        }
        return value.trim().toLowerCase();
    }
}
