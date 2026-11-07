package com.bookeasy.recommend;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.common.ratelimit.RateLimiter;

/**
 * Nearby offers worth knowing about. The database supplies every fact (offers, free trials, restaurant discounts within
 * a few km); {@link AiRanker} only chooses among them and explains why. Picks that don't match a real candidate are
 * dropped, and without an API key (or when the AI call fails) a simple scoring rule ranks the same candidates.
 */
@Service
public class RecommendationService {

    static final double MAX_RADIUS_KM = 20;
    static final int MAX_CANDIDATES = 40;
    static final int MAX_PICKS = 6;
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);

    private final NamedParameterJdbcTemplate jdbc;
    private final AiRanker ranker;
    private final RateLimiter rateLimiter;
    private final Clock clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public RecommendationService(NamedParameterJdbcTemplate jdbc, AiRanker ranker, RateLimiter rateLimiter, Clock clock) {
        this.jdbc = jdbc;
        this.ranker = ranker;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /** One real offer near the user. id: p&lt;promotion&gt;, d&lt;restaurant&gt; or f&lt;free service&gt;. */
    public record Candidate(String id, String type, String businessName, String slug, String category, String title,
                            String details, Integer percentOff, double distanceKm, BigDecimal rating, int reviewCount,
                            String coverUrl) {
    }

    public record Pick(String id, String headline, String reason) {
    }

    /** source: AI when Claude chose the picks, RULES when the fallback ranking did. */
    public record Recommendation(Candidate offer, String headline, String reason) {
    }

    public record Recommendations(String source, String intro, List<Recommendation> items) {
    }

    private record Cached(Recommendations value, Instant at) {
    }

    @Transactional(readOnly = true)
    public Recommendations recommend(double lat, double lng, double radiusKm, String query, String language, String ip) {
        String q = query == null ? "" : query.trim();
        if (q.length() > 100) {
            q = q.substring(0, 100);
        }
        String lang = language == null || language.isBlank() ? "en" : language.toLowerCase(Locale.ROOT);
        double radius = Math.min(Math.max(radiusKm, 1), MAX_RADIUS_KM);
        Instant now = clock.instant();
        // Same area (about 1 km), request, language and hour: reuse the answer instead of paying for another call.
        String key = String.format(Locale.ROOT, "%.2f|%.2f|%.0f|%s|%s|%d", lat, lng, radius, q.toLowerCase(Locale.ROOT), lang,
                now.atZone(PromotionService.IST).getHour());
        Cached hit = cache.get(key);
        if (hit != null && hit.at().plus(CACHE_TTL).isAfter(now)) {
            return hit.value();
        }
        List<Candidate> candidates = candidates(lat, lng, radius);
        Recommendations result;
        if (candidates.isEmpty()) {
            result = new Recommendations("RULES", null, List.of());
        } else {
            Optional<AiRanker.Ranked> ai = Optional.empty();
            if (ranker.enabled()) {
                // AI calls cost money: at most 30 fresh answers an hour per visitor; the rules take over beyond that.
                try {
                    rateLimiter.hit("recommend:ip:" + ip, 30, Duration.ofHours(1));
                    ai = ranker.rank(candidates, q, lang, now);
                } catch (com.bookeasy.common.exception.ApiException limited) {
                    ai = Optional.empty();
                }
            }
            String request = q;
            result = ai.map(r -> fromPicks("AI", r.intro(), r.picks(), candidates))
                    .filter(r -> !r.items().isEmpty())
                    .orElseGet(() -> byRules(candidates, request));
        }
        if (cache.size() > 500) {
            cache.clear();
        }
        cache.put(key, new Cached(result, now));
        return result;
    }

    /** Keeps only picks that name a real candidate, once each, in the order given. */
    static Recommendations fromPicks(String source, String intro, List<Pick> picks, List<Candidate> candidates) {
        Map<String, Candidate> byId = new HashMap<>();
        candidates.forEach(c -> byId.put(c.id(), c));
        Map<String, Recommendation> out = new LinkedHashMap<>();
        for (Pick p : picks) {
            Candidate c = p.id() == null ? null : byId.get(p.id().trim());
            if (c != null && !out.containsKey(c.id()) && out.size() < MAX_PICKS) {
                out.put(c.id(), new Recommendation(c, clip(p.headline(), 80, c.title()), clip(p.reason(), 200, null)));
            }
        }
        return new Recommendations(source, clip(intro, 240, null), List.copyOf(out.values()));
    }

    /** Free things and big savings first, then rating, minus distance; at most two picks per category. */
    static Recommendations byRules(List<Candidate> candidates, String query) {
        String[] words = query == null ? new String[0] : query.toLowerCase(Locale.ROOT).split("\\W+");
        List<Candidate> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingDouble((Candidate c) -> -score(c, words)));
        Map<String, Integer> perCategory = new HashMap<>();
        List<Recommendation> items = new ArrayList<>();
        for (Candidate c : sorted) {
            if (items.size() == MAX_PICKS) {
                break;
            }
            if (perCategory.merge(c.category(), 1, Integer::sum) > 2) {
                continue;
            }
            String reason = String.format(Locale.ROOT, "%s · %.1f km away%s", c.category(), c.distanceKm(),
                    c.reviewCount() > 0 ? String.format(Locale.ROOT, " · rated %.1f", c.rating().doubleValue()) : "");
            items.add(new Recommendation(c, c.title(), reason));
        }
        return new Recommendations("RULES", null, items);
    }

    static double score(Candidate c, String[] words) {
        double s = switch (c.type()) {
            case "FREE", "FREE_SERVICE" -> 3;
            case "PACKAGE" -> 2.5;
            case "EVENT" -> 2;
            default -> 0;
        };
        if (c.percentOff() != null) {
            s += Math.min(4, c.percentOff() / 10.0);
        }
        if (c.reviewCount() > 0) {
            s += c.rating().doubleValue() * 0.3;
        }
        s -= c.distanceKm() * 0.4;
        String text = (c.businessName() + " " + c.category() + " " + c.title() + " " + (c.details() == null ? "" : c.details()))
                .toLowerCase(Locale.ROOT);
        for (String w : words) {
            if (w.length() > 2 && text.contains(w)) {
                s += 3;
            }
        }
        return s;
    }

    private static String clip(String text, int max, String fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        String t = text.trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    // ----- candidates -----

    private static final String COMMON = """
            o.name AS org_name, o.slug, o.rating_avg, o.review_count,
            JSON_UNQUOTE(JSON_EXTRACT(sc.name_i18n, '$.en')) AS category,
            (SELECT f.public_id FROM uploaded_files f WHERE f.id = o.cover_file_id) AS cover_id,
            MIN(ST_Distance_Sphere(b.location, ST_SRID(POINT(:lng, :lat), 4326))) AS distance_m
            """;
    private static final String FROM_ORG = """
            JOIN organizations o ON o.id = X.organization_id AND o.verification_status = 'APPROVED'
            JOIN subcategories sc ON sc.id = o.subcategory_id
            JOIN organization_branches b ON b.organization_id = o.id AND b.is_active
            """;

    List<Candidate> candidates(double lat, double lng, double radiusKm) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("lat", lat).addValue("lng", lng)
                .addValue("radiusM", radiusKm * 1000).addValue("today", Date.valueOf(LocalDate.ofInstant(clock.instant(), PromotionService.IST)));
        List<Candidate> all = new ArrayList<>();
        // Offers businesses published (current ones only).
        jdbc.query("SELECT pr.id, pr.title, pr.details, pr.kind, pr.percent_off, " + COMMON + " FROM promotions pr "
                        + FROM_ORG.replace("X.", "pr.")
                        + " WHERE (pr.valid_from IS NULL OR pr.valid_from <= :today) AND (pr.valid_until IS NULL OR pr.valid_until >= :today)"
                        + " GROUP BY pr.id HAVING distance_m <= :radiusM ORDER BY distance_m LIMIT 25", p,
                rs -> {
                    all.add(candidate("p" + rs.getLong("id"), rs.getString("kind"), rs, rs.getString("title"), rs.getString("details"),
                            (Integer) rs.getObject("percent_off", Integer.class)));
                });
        // Restaurants: their best discount on the bill.
        jdbc.query("SELECT d.organization_id AS oid, MAX(d.percent) AS pct, " + COMMON + " FROM dining_offers d "
                        + FROM_ORG.replace("X.", "d.") + " GROUP BY d.organization_id HAVING distance_m <= :radiusM ORDER BY distance_m LIMIT 15", p,
                rs -> {
                    int pct = rs.getInt("pct");
                    all.add(candidate("d" + rs.getLong("oid"), "DISCOUNT", rs, "Up to " + pct + "% off the bill",
                            "Book a table to get the discount on the final bill", pct));
                });
        // Free sessions (trial classes, free assessments).
        jdbc.query("SELECT MIN(sv.id) AS sid, ANY_VALUE(sv.name) AS sname, " + COMMON + " FROM services sv "
                        + FROM_ORG.replace("X.", "sv.")
                        + " WHERE sv.is_active AND sv.price = 0 GROUP BY sv.organization_id HAVING distance_m <= :radiusM"
                        + " ORDER BY distance_m LIMIT 15", p,
                rs -> {
                    all.add(candidate("f" + rs.getLong("sid"), "FREE_SERVICE", rs, rs.getString("sname"), "Free – book a slot online", null));
                });
        all.sort(Comparator.comparingDouble(Candidate::distanceKm));
        return all.size() > MAX_CANDIDATES ? List.copyOf(all.subList(0, MAX_CANDIDATES)) : all;
    }

    private static Candidate candidate(String id, String type, java.sql.ResultSet rs, String title, String details, Integer percent)
            throws java.sql.SQLException {
        String cover = rs.getString("cover_id");
        return new Candidate(id, type, rs.getString("org_name"), rs.getString("slug"), rs.getString("category"), title, details,
                percent, Math.round(rs.getDouble("distance_m") / 100.0) / 10.0, rs.getBigDecimal("rating_avg"), rs.getInt("review_count"),
                cover == null ? null : "/api/v1/public/files/" + cover);
    }
}
