package com.bookeasy.discovery;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.bookeasy.catalog.BranchService;
import com.bookeasy.catalog.BranchServiceRepository;
import com.bookeasy.catalog.ServiceOffering;
import com.bookeasy.catalog.ServiceRepository;
import com.bookeasy.category.entity.Category;
import com.bookeasy.category.entity.Subcategory;
import com.bookeasy.category.repository.CategoryRepository;
import com.bookeasy.category.repository.SubcategoryRepository;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.file.UploadedFile;
import com.bookeasy.file.UploadedFileRepository;
import com.bookeasy.organization.dto.OrganizationDtos.DayHoursView;
import com.bookeasy.organization.entity.Branch;
import com.bookeasy.organization.entity.ConfirmationMode;
import com.bookeasy.organization.entity.Organization;
import com.bookeasy.organization.entity.OrganizationStatus;
import com.bookeasy.organization.repository.BranchRepository;
import com.bookeasy.organization.repository.OrganizationRepository;
import com.bookeasy.organization.service.OrganizationViews;
import com.bookeasy.staff.StaffProfile;
import com.bookeasy.staff.StaffProfileRepository;

/** Customer-facing discovery: nearby search, suggestions and public business profiles (approved businesses only). */
@Service
public class DiscoveryService {

    public static final int MAX_RADIUS_KM = 100;
    private static final double KM_PER_DEGREE_LAT = 111.32;

    private final NamedParameterJdbcTemplate jdbc;
    private final OrganizationRepository organizations;
    private final BranchRepository branches;
    private final ServiceRepository services;
    private final BranchServiceRepository branchServices;
    private final StaffProfileRepository staff;
    private final SubcategoryRepository subcategories;
    private final CategoryRepository categories;
    private final UploadedFileRepository files;
    private final OrganizationViews views;

    public DiscoveryService(NamedParameterJdbcTemplate jdbc, OrganizationRepository organizations,
                            BranchRepository branches, ServiceRepository services,
                            BranchServiceRepository branchServices, StaffProfileRepository staff,
                            SubcategoryRepository subcategories, CategoryRepository categories,
                            UploadedFileRepository files, OrganizationViews views) {
        this.jdbc = jdbc;
        this.organizations = organizations;
        this.branches = branches;
        this.services = services;
        this.branchServices = branchServices;
        this.staff = staff;
        this.subcategories = subcategories;
        this.categories = categories;
        this.files = files;
        this.views = views;
    }

    public enum Sort { DISTANCE, RATING, PRICE }

    public record SearchQuery(double lat, double lng, double radiusKm, Long categoryId, Long subcategoryId, String q,
                              BigDecimal minRating, Sort sort, int page, int size) {
    }

    public record SearchHit(long organizationId, String slug, String name, BigDecimal ratingAvg, int reviewCount,
                            String logoUrl, String coverUrl, long categoryId, long subcategoryId, Map<String, String> subcategoryName,
                            long branchId, String branchName, String locality, String city, BigDecimal latitude,
                            BigDecimal longitude, double distanceKm, BigDecimal fromPrice) {
    }

    public record SearchPage(List<SearchHit> items, int page, int size, boolean hasMore) {
    }

    /**
     * Branches of approved businesses within the radius. One row per branch; a business with several nearby branches
     * appears once per branch so each distance is honest.
     */
    @Transactional(readOnly = true)
    public SearchPage search(SearchQuery query) {
        double radius = Math.min(Math.max(query.radiusKm(), 1), MAX_RADIUS_KM);
        double dLat = radius / KM_PER_DEGREE_LAT;
        double dLng = radius / (KM_PER_DEGREE_LAT * Math.max(Math.cos(Math.toRadians(query.lat())), 0.01));
        int size = Math.min(Math.max(query.size(), 1), 50);
        int page = Math.max(query.page(), 0);

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("lat", query.lat()).addValue("lng", query.lng())
                .addValue("minLat", query.lat() - dLat).addValue("maxLat", query.lat() + dLat)
                .addValue("minLng", query.lng() - dLng).addValue("maxLng", query.lng() + dLng)
                .addValue("radiusM", radius * 1000)
                .addValue("limit", size + 1).addValue("offset", page * size);
        StringBuilder where = new StringBuilder("""
                o.verification_status = 'APPROVED' AND b.is_active
                AND b.latitude BETWEEN :minLat AND :maxLat AND b.longitude BETWEEN :minLng AND :maxLng
                """);
        if (query.categoryId() != null) {
            where.append(" AND s.category_id = :categoryId");
            p.addValue("categoryId", query.categoryId());
        }
        if (query.subcategoryId() != null) {
            where.append(" AND o.subcategory_id = :subcategoryId");
            p.addValue("subcategoryId", query.subcategoryId());
        }
        if (query.minRating() != null) {
            where.append(" AND o.rating_avg >= :minRating");
            p.addValue("minRating", query.minRating());
        }
        if (StringUtils.hasText(query.q())) {
            where.append("""
                     AND (LOWER(o.name) LIKE :q OR LOWER(CAST(s.name_i18n AS CHAR)) LIKE :q
                          OR EXISTS (SELECT 1 FROM branch_services bs2 JOIN services sv2 ON sv2.id = bs2.service_id
                                     WHERE bs2.branch_id = b.id AND sv2.is_active AND LOWER(sv2.name) LIKE :q))
                    """);
            p.addValue("q", "%" + query.q().trim().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%");
        }
        String order = switch (query.sort() == null ? Sort.DISTANCE : query.sort()) {
            // The ORDER BY sits outside the derived table, so it must use the derived table's column names.
            case RATING -> "rating_avg DESC, review_count DESC, distance_m ASC";
            case PRICE -> "min_price IS NULL, min_price ASC, distance_m ASC";
            case DISTANCE -> "distance_m ASC";
        };
        String sql = """
                SELECT * FROM (
                  SELECT o.id AS org_id, o.slug, o.name, o.rating_avg, o.review_count, o.logo_file_id, o.cover_file_id,
                         s.category_id, o.subcategory_id, CAST(s.name_i18n AS CHAR) AS sub_name,
                         b.id AS branch_id, b.name AS branch_name, b.locality, b.city, b.latitude, b.longitude,
                         ST_Distance_Sphere(b.location, ST_SRID(POINT(:lng, :lat), 4326)) AS distance_m,
                         (SELECT MIN(COALESCE(bs.price_override, sv.price)) FROM branch_services bs
                            JOIN services sv ON sv.id = bs.service_id
                           WHERE bs.branch_id = b.id AND sv.is_active) AS min_price
                  FROM organization_branches b
                  JOIN organizations o ON o.id = b.organization_id
                  JOIN subcategories s ON s.id = o.subcategory_id
                  WHERE\s""" + where + """
                ) x WHERE distance_m <= :radiusM
                ORDER BY\s""" + order + " LIMIT :limit OFFSET :offset";

        List<Object[]> raw = new ArrayList<>();
        jdbc.query(sql, p, rs -> {
            raw.add(new Object[]{rs.getLong("org_id"), rs.getString("slug"), rs.getString("name"),
                    rs.getBigDecimal("rating_avg"), rs.getInt("review_count"), (Long) rs.getObject("logo_file_id", Long.class),
                    rs.getLong("category_id"), rs.getLong("subcategory_id"), rs.getString("sub_name"),
                    rs.getLong("branch_id"), rs.getString("branch_name"), rs.getString("locality"), rs.getString("city"),
                    rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude"), rs.getDouble("distance_m"),
                    rs.getBigDecimal("min_price"), (Long) rs.getObject("cover_file_id", Long.class)});
        });
        boolean hasMore = raw.size() > size;
        List<Object[]> rows = hasMore ? raw.subList(0, size) : raw;
        Map<Long, String> logos = files.findAllById(rows.stream()
                .flatMap(r -> java.util.stream.Stream.of((Long) r[5], (Long) r[17]))
                .filter(java.util.Objects::nonNull).distinct().toList()).stream().collect(Collectors.toMap(UploadedFile::getId, UploadedFile::publicUrl));
        List<SearchHit> hits = rows.stream().map(r -> new SearchHit((Long) r[0], (String) r[1], (String) r[2],
                (BigDecimal) r[3], (Integer) r[4], r[5] == null ? null : logos.get((Long) r[5]),
                r[17] == null ? null : logos.get((Long) r[17]), (Long) r[6], (Long) r[7],
                parseNames((String) r[8]), (Long) r[9], (String) r[10], (String) r[11], (String) r[12],
                (BigDecimal) r[13], (BigDecimal) r[14],
                BigDecimal.valueOf((Double) r[15] / 1000).setScale(1, RoundingMode.HALF_UP).doubleValue(),
                (BigDecimal) r[16])).toList();
        return new SearchPage(hits, page, size, hasMore);
    }

    public record Suggestion(String type, Long id, String slug, String label, Map<String, String> labels) {
    }

    /** Business names and subcategories (in any language) matching the typed text. */
    @Transactional(readOnly = true)
    public List<Suggestion> suggest(String q) {
        if (!StringUtils.hasText(q) || q.trim().length() < 2) {
            return List.of();
        }
        String like = "%" + q.trim().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
        MapSqlParameterSource p = new MapSqlParameterSource("q", like);
        List<Suggestion> out = new ArrayList<>(jdbc.query("""
                SELECT s.id, CAST(s.name_i18n AS CHAR) AS names FROM subcategories s JOIN categories c ON c.id = s.category_id
                WHERE s.is_active AND c.is_active AND LOWER(CAST(s.name_i18n AS CHAR)) LIKE :q ORDER BY s.sort_order LIMIT 4
                """, p, (rs, i) -> new Suggestion("SUBCATEGORY", rs.getLong("id"), null, null, parseNames(rs.getString("names")))));
        out.addAll(jdbc.query("""
                SELECT o.id, o.slug, o.name FROM organizations o
                WHERE o.verification_status = 'APPROVED' AND LOWER(o.name) LIKE :q ORDER BY o.rating_avg DESC, o.name LIMIT 6
                """, p, (rs, i) -> new Suggestion("BUSINESS", rs.getLong("id"), rs.getString("slug"), rs.getString("name"), null)));
        return out;
    }

    // ----- public profile -----

    public record PublicService(long id, String name, String description, int durationMinutes, BigDecimal price,
                                ServiceOffering.ConsultationType consultationType, boolean requiresStaff,
                                Map<Long, BigDecimal> branchPrices, String imageUrl) {
    }

    public record PublicStaff(long id, String displayName, String title, String specialisation, List<Long> branchIds,
                              List<Long> serviceIds) {
    }

    public record PublicBranch(long id, String name, String addressLine1, String addressLine2, String locality, String city,
                               String state, String pincode, BigDecimal latitude, BigDecimal longitude, String phone,
                               String timezone, List<DayHoursView> hours) {
    }

    public record PublicProfile(long id, String slug, String name, String description, String logoUrl, String coverUrl,
                                Map<String, String> categoryName, Map<String, String> subcategoryName, long categoryId,
                                String bookingWorkflow, BigDecimal ratingAvg, int reviewCount, String contactPhone,
                                String contactEmail, String website, ConfirmationMode confirmationMode,
                                List<PublicBranch> branches, List<PublicService> services, List<PublicStaff> staff) {
    }

    @Transactional(readOnly = true)
    public PublicProfile profile(String slug) {
        Organization org = organizations.findBySlug(slug)
                .filter(o -> o.getStatus() == OrganizationStatus.APPROVED)
                .orElseThrow(() -> ApiException.notFound("Business"));
        return profile(org);
    }

    private PublicProfile profile(Organization org) {
        Subcategory sub = subcategories.findById(org.getSubcategoryId()).orElseThrow();
        Category cat = categories.findById(sub.getCategoryId()).orElseThrow();
        List<Branch> active = branches.findByOrganizationIdOrderByIdAsc(org.getId()).stream().filter(Branch::isActive).toList();
        var hours = views.hoursByBranch(active);
        List<ServiceOffering> svc = services.findByOrganizationIdOrderByNameAsc(org.getId()).stream()
                .filter(ServiceOffering::isActive).toList();
        java.util.Set<Long> activeBranchIds = active.stream().map(Branch::getId).collect(Collectors.toSet());
        Map<Long, List<BranchService>> offers = branchServices.findByServiceIdIn(svc.stream().map(ServiceOffering::getId).toList())
                .stream().filter(o -> activeBranchIds.contains(o.getBranchId()))
                .collect(Collectors.groupingBy(BranchService::getServiceId));
        java.util.Set<Long> photoIds = new java.util.HashSet<>();
        svc.stream().map(ServiceOffering::getImageFileId).filter(java.util.Objects::nonNull).forEach(photoIds::add);
        if (org.getLogoFileId() != null) {
            photoIds.add(org.getLogoFileId());
        }
        if (org.getCoverFileId() != null) {
            photoIds.add(org.getCoverFileId());
        }
        Map<Long, String> photos = files.findAllById(photoIds).stream()
                .filter(f -> f.publicUrl() != null)
                .collect(Collectors.toMap(UploadedFile::getId, UploadedFile::publicUrl));
        List<PublicService> publicServices = svc.stream().filter(s -> offers.containsKey(s.getId()))
                .map(s -> new PublicService(s.getId(), s.getName(), s.getDescription(), s.getDurationMinutes(), s.getPrice(),
                        s.getConsultationType(), s.isRequiresStaff(), offers.get(s.getId()).stream().collect(Collectors.toMap(
                        BranchService::getBranchId, o -> o.getPriceOverride() != null ? o.getPriceOverride() : s.getPrice())),
                        s.getImageFileId() == null ? null : photos.get(s.getImageFileId())))
                .toList();
        List<PublicStaff> people = staff.findByOrganizationIdAndStatusIn(org.getId(), List.of(StaffProfile.Status.ACTIVE))
                .stream().sorted(Comparator.comparing(StaffProfile::getDisplayName))
                .map(s -> new PublicStaff(s.getId(), s.getDisplayName(), s.getTitle(), s.getSpecialisation(),
                        s.getBranchIds().stream().sorted().toList(), s.getServiceIds().stream().sorted().toList()))
                .toList();
        String logo = org.getLogoFileId() == null ? null : photos.get(org.getLogoFileId());
        String cover = org.getCoverFileId() == null ? null : photos.get(org.getCoverFileId());
        return new PublicProfile(org.getId(), org.getSlug(), org.getName(), org.getDescription(), logo, cover,
                cat.getName(),
                sub.getName(), cat.getId(), cat.getBookingWorkflow().name(), org.getRatingAvg(), org.getReviewCount(),
                org.getContactPhone(), org.getContactEmail(), org.getWebsite(), org.getConfirmationMode(),
                active.stream().map(b -> new PublicBranch(b.getId(), b.getName(), b.getAddressLine1(), b.getAddressLine2(),
                        b.getLocality(), b.getCity(), b.getState(), b.getPincode(), b.getLatitude(), b.getLongitude(),
                        b.getPhone(), b.getTimezone(), hours.getOrDefault(b.getId(), List.of()))).toList(),
                publicServices, people);
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    private static Map<String, String> parseNames(String json) {
        try {
            return JSON.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() { });
        } catch (Exception e) {
            return Map.of();
        }
    }
}
