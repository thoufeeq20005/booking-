package com.bookeasy.discovery;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The search window: "Trending near you" when the box is empty, and live results while typing, across movies,
 * businesses (restaurants, cinemas, clinics, salons, gyms, ...) and events businesses publish. Filtered by a tab:
 * "all", or a category slug ("movies", "dining", "healthcare", ...).
 */
@Service
public class QuickSearchService {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final double RADIUS_M = 30_000;
    static final int LIMIT = 16;
    private static final String FILE_URL = "/api/v1/public/files/";
    /** Public photo id for a file id column, or null. */
    private static final String PHOTO = "(SELECT f.public_id FROM uploaded_files f WHERE f.id = %s AND f.visibility = 'PUBLIC')";
    private static final String HERE = "ST_SRID(POINT(:lng, :lat), 4326)";

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public QuickSearchService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * kind: MOVIE, BUSINESS or EVENT. category: a business's subcategory name by language (null for movies and events);
     * detail: the second line (certificate and languages, the area, or the business holding the event). Movies without a poster photo
     * carry their two poster colours; events carry their last day.
     */
    public record Item(String kind, String title, java.util.Map<String, String> category, String detail, String imageUrl, String href, String posterFrom,
                       String posterTo, LocalDate until) {
    }

    /** trending: true when the box was empty (or one letter), so the window can say "Trending near you". */
    public record Result(boolean trending, List<Item> items) {
    }

    @Transactional(readOnly = true)
    public Result search(String query, String tab, double lat, double lng) {
        String q = query == null ? "" : query.trim();
        if (q.length() > 60) {
            q = q.substring(0, 60);
        }
        String t = tab == null || tab.isBlank() ? "all" : tab.trim().toLowerCase(Locale.ROOT);
        boolean typing = q.length() >= 2;
        String escaped = q.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        Instant now = clock.instant();
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("lat", lat).addValue("lng", lng).addValue("r", RADIUS_M)
                .addValue("q", "%" + escaped + "%").addValue("prefix", escaped + "%").addValue("tab", t)
                .addValue("today", java.sql.Date.valueOf(LocalDate.ofInstant(now, IST)))
                .addValue("from", Timestamp.from(now)).addValue("until", Timestamp.from(now.plus(Duration.ofDays(8))));

        List<Item> movies = t.equals("all") || t.equals("movies") ? movies(p, typing) : List.of();
        List<Item> businesses = businesses(p, typing);
        List<Item> events = events(p, typing);
        return new Result(!typing, interleave(movies, businesses, events));
    }

    /** One movie, two businesses, one event, repeated, so the first screen shows a bit of everything. */
    static List<Item> interleave(List<Item> movies, List<Item> businesses, List<Item> events) {
        List<Item> out = new ArrayList<>();
        Iterator<Item> mv = movies.iterator();
        Iterator<Item> bz = businesses.iterator();
        Iterator<Item> ev = events.iterator();
        while (out.size() < LIMIT && (mv.hasNext() || bz.hasNext() || ev.hasNext())) {
            if (mv.hasNext()) {
                out.add(mv.next());
            }
            for (int i = 0; i < 2 && bz.hasNext() && out.size() < LIMIT; i++) {
                out.add(bz.next());
            }
            if (ev.hasNext() && out.size() < LIMIT) {
                out.add(ev.next());
            }
        }
        return out;
    }

    /** Typing: titles that match. Empty: movies with shows nearby in the next week, newest first. */
    private List<Item> movies(MapSqlParameterSource p, boolean typing) {
        String select = "SELECT m.id, m.title, m.certificate, m.languages, m.poster_from, m.poster_to, "
                + PHOTO.formatted("m.poster_file_id") + " AS photo FROM movies m WHERE m.is_active ";
        String sql = typing
                ? select + "AND LOWER(m.title) LIKE :q ORDER BY LOWER(m.title) LIKE :prefix DESC, m.release_date DESC LIMIT 6"
                : select + """
                  AND m.id IN (
                    SELECT st.movie_id FROM showtimes st JOIN cinema_screens sc ON sc.id = st.screen_id
                    JOIN organization_branches b ON b.id = sc.branch_id
                    WHERE st.starts_at > :from AND st.starts_at < :until AND b.is_active
                      AND ST_Distance_Sphere(b.location, %s) <= :r)
                  ORDER BY m.release_date DESC, m.title LIMIT 6""".formatted(HERE);
        return jdbc.query(sql, p, (rs, i) -> new Item("MOVIE", rs.getString("title"), null,
                rs.getString("certificate") + " · " + rs.getString("languages"), url(rs.getString("photo")),
                "/movies/" + rs.getLong("id"), rs.getString("poster_from"), rs.getString("poster_to"), null));
    }

    /** Typing: names, subcategories, cuisines or dishes that match, nearest first. Empty: the best rated nearby. */
    private List<Item> businesses(MapSqlParameterSource p, boolean typing) {
        String match = typing ? """
                AND (LOWER(o.name) LIKE :q OR LOWER(CAST(s.name_i18n AS CHAR)) LIKE :q
                     OR EXISTS (SELECT 1 FROM venue_profiles vp WHERE vp.organization_id = o.id AND LOWER(vp.cuisines) LIKE :q)
                     OR EXISTS (SELECT 1 FROM menu_items mi WHERE mi.organization_id = o.id AND LOWER(mi.name) LIKE :q))
                """ : "";
        String order = typing ? "LOWER(name) LIKE :prefix DESC, distance_m" : "rating_avg DESC, review_count DESC, distance_m";
        String sql = """
                SELECT * FROM (
                  SELECT o.slug, o.name, o.rating_avg, o.review_count, CAST(s.name_i18n AS CHAR) AS category,
                         %1$s AS photo,
                         (SELECT CONCAT_WS(', ', b.locality, b.city) FROM organization_branches b
                          WHERE b.organization_id = o.id AND b.is_active
                          ORDER BY ST_Distance_Sphere(b.location, %2$s) LIMIT 1) AS area,
                         (SELECT MIN(ST_Distance_Sphere(b.location, %2$s)) FROM organization_branches b
                          WHERE b.organization_id = o.id AND b.is_active) AS distance_m
                  FROM organizations o JOIN subcategories s ON s.id = o.subcategory_id JOIN categories c ON c.id = s.category_id
                  WHERE o.verification_status = 'APPROVED' AND (:tab = 'all' OR c.slug = :tab)
                  %3$s
                ) x WHERE distance_m <= :r ORDER BY %4$s LIMIT 12""".formatted(PHOTO.formatted("o.cover_file_id"), HERE, match, order);
        return jdbc.query(sql, p, (rs, i) -> new Item("BUSINESS", rs.getString("name"), DiscoveryService.parseNames(rs.getString("category")),
                rs.getString("area"), url(rs.getString("photo")), "/b/" + rs.getString("slug"), null, null, null));
    }

    /** Events businesses publish (camps, workshops, live music) that are on now or coming up, nearby. */
    private List<Item> events(MapSqlParameterSource p, boolean typing) {
        String match = typing ? "AND (LOWER(pr.title) LIKE :q OR LOWER(o.name) LIKE :q)" : "";
        String sql = """
                SELECT * FROM (
                  SELECT pr.title, pr.valid_until, o.slug, o.name, %1$s AS photo,
                         (SELECT MIN(ST_Distance_Sphere(b.location, %2$s)) FROM organization_branches b
                          WHERE b.organization_id = o.id AND b.is_active) AS distance_m
                  FROM promotions pr JOIN organizations o ON o.id = pr.organization_id AND o.verification_status = 'APPROVED'
                  JOIN subcategories s ON s.id = o.subcategory_id JOIN categories c ON c.id = s.category_id
                  WHERE pr.kind = 'EVENT' AND (pr.valid_until IS NULL OR pr.valid_until >= :today)
                    AND (:tab = 'all' OR c.slug = :tab)
                  %3$s
                ) x WHERE distance_m <= :r ORDER BY valid_until IS NULL, valid_until, distance_m LIMIT 6"""
                .formatted(PHOTO.formatted("o.cover_file_id"), HERE, match);
        return jdbc.query(sql, p, (rs, i) -> {
            java.sql.Date until = rs.getDate("valid_until");
            return new Item("EVENT", rs.getString("title"), null, rs.getString("name"), url(rs.getString("photo")),
                    "/b/" + rs.getString("slug"), null, null, until == null ? null : until.toLocalDate());
        });
    }

    private static String url(String publicId) {
        return publicId == null ? null : FILE_URL + publicId;
    }
}
