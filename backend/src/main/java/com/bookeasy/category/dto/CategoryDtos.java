package com.bookeasy.category.dto;

import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import com.bookeasy.category.entity.BookingWorkflow;
import com.bookeasy.category.entity.Category;
import com.bookeasy.category.entity.DocumentRequirement;
import com.bookeasy.category.entity.Subcategory;

public final class CategoryDtos {

    /** Largest document an admin can allow; matches the multipart limit in application.yml. */
    public static final int MAX_DOCUMENT_BYTES = 10 * 1024 * 1024;

    public static final String SLUG_PATTERN = "^[a-z0-9]+(?:-[a-z0-9]+)*$";

    private CategoryDtos() {
    }

    // ----- requests -----

    public record CategoryRequest(
            @NotBlank @Pattern(regexp = SLUG_PATTERN) @jakarta.validation.constraints.Size(max = 64) String slug,
            @NotNull Map<String, String> name,
            Map<String, String> description,
            @NotBlank @Pattern(regexp = "^[a-z0-9-]{1,48}$") String icon,
            @NotNull BookingWorkflow bookingWorkflow,
            Boolean active,
            @Min(0) @Max(100000) Integer sortOrder) {
    }

    public record SubcategoryRequest(
            @NotBlank @Pattern(regexp = SLUG_PATTERN) @jakarta.validation.constraints.Size(max = 64) String slug,
            @NotNull Map<String, String> name,
            Boolean active,
            @Min(0) @Max(100000) Integer sortOrder) {
    }

    public record RequirementRequest(
            @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,47}$") String code,
            @NotNull Map<String, String> label,
            Map<String, String> description,
            Boolean mandatory,
            @NotEmpty Set<String> allowedTypes,
            @Min(10 * 1024) @Max(MAX_DOCUMENT_BYTES) int maxSizeBytes,
            Boolean active,
            @Min(0) @Max(100000) Integer sortOrder) {
    }

    // ----- views -----

    public record SubcategoryView(long id, long categoryId, String slug, Map<String, String> name, boolean active,
                                  int sortOrder) {

        public static SubcategoryView of(Subcategory s) {
            return new SubcategoryView(s.getId(), s.getCategoryId(), s.getSlug(), s.getName(), s.isActive(),
                    s.getSortOrder());
        }
    }

    public record RequirementView(long id, long categoryId, String code, Map<String, String> label,
                                  Map<String, String> description, boolean mandatory, List<String> allowedTypes,
                                  int maxSizeBytes, boolean active, int sortOrder) {

        public static RequirementView of(DocumentRequirement r) {
            return new RequirementView(r.getId(), r.getCategoryId(), r.getCode(), r.getLabel(), r.getDescription(),
                    r.isMandatory(), List.copyOf(r.allowedTypes()), r.getMaxSizeBytes(), r.isActive(), r.getSortOrder());
        }
    }

    /** {@code documentRequirements} is only filled for admins (null fields are omitted from JSON). */
    public record CategoryView(long id, String slug, Map<String, String> name, Map<String, String> description,
                               String icon, BookingWorkflow bookingWorkflow, boolean active, int sortOrder,
                               List<SubcategoryView> subcategories, List<RequirementView> documentRequirements) {

        public static CategoryView of(Category c, List<SubcategoryView> subs, List<RequirementView> reqs) {
            return new CategoryView(c.getId(), c.getSlug(), c.getName(), c.getDescription(), c.getIcon(),
                    c.getBookingWorkflow(), c.isActive(), c.getSortOrder(), subs, reqs);
        }
    }
}
