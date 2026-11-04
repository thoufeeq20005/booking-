package com.bookeasy.category;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.audit.AuditService;
import com.bookeasy.category.dto.CategoryDtos.CategoryRequest;
import com.bookeasy.category.dto.CategoryDtos.CategoryView;
import com.bookeasy.category.dto.CategoryDtos.RequirementRequest;
import com.bookeasy.category.dto.CategoryDtos.SubcategoryRequest;
import com.bookeasy.category.entity.Category;
import com.bookeasy.category.entity.DocumentRequirement;
import com.bookeasy.category.entity.Subcategory;
import com.bookeasy.category.repository.CategoryRepository;
import com.bookeasy.category.repository.DocumentRequirementRepository;
import com.bookeasy.category.repository.SubcategoryRepository;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.bookeasy.common.security.AuthenticatedUser;
import com.bookeasy.common.text.LocalizedText;
import com.bookeasy.file.ContentSniffer;
import com.bookeasy.organization.repository.OrganizationDocumentRepository;
import com.bookeasy.organization.repository.OrganizationRepository;

/**
 * Super Admin catalogue management. Anything a business already uses can be deactivated (hidden from new
 * registrations) but not deleted. Every change is audited. Mutations return the whole admin tree.
 */
@Service
public class AdminCategoryService {

    static final Set<String> DOCUMENT_TYPES = Set.of(ContentSniffer.PDF, ContentSniffer.JPEG, ContentSniffer.PNG,
            ContentSniffer.WEBP);

    private final CategoryRepository categories;
    private final SubcategoryRepository subcategories;
    private final DocumentRequirementRepository requirements;
    private final OrganizationRepository organizations;
    private final OrganizationDocumentRepository organizationDocuments;
    private final CategoryService categoryService;
    private final AuditService audit;
    private final Clock clock;

    public AdminCategoryService(CategoryRepository categories, SubcategoryRepository subcategories,
                                DocumentRequirementRepository requirements, OrganizationRepository organizations,
                                OrganizationDocumentRepository organizationDocuments, CategoryService categoryService,
                                AuditService audit, Clock clock) {
        this.categories = categories;
        this.subcategories = subcategories;
        this.requirements = requirements;
        this.organizations = organizations;
        this.organizationDocuments = organizationDocuments;
        this.categoryService = categoryService;
        this.audit = audit;
        this.clock = clock;
    }

    // ----- categories -----

    @Transactional
    public List<CategoryView> createCategory(AuthenticatedUser admin, CategoryRequest req, String ip) {
        if (categories.existsBySlug(req.slug())) {
            throw new ApiException(ErrorCode.SLUG_TAKEN);
        }
        Instant now = clock.instant();
        Category c = new Category(req.slug(), now);
        applyCategory(c, req, now);
        categories.save(c);
        record(admin, "CATEGORY_CREATED", "CATEGORY", c.getId(), Map.of("slug", c.getSlug()), ip);
        return categoryService.adminTree();
    }

    @Transactional
    public List<CategoryView> updateCategory(AuthenticatedUser admin, long id, CategoryRequest req, String ip) {
        Category c = categories.findById(id).orElseThrow(() -> ApiException.notFound("Category"));
        if (categories.existsBySlugAndIdNot(req.slug(), id)) {
            throw new ApiException(ErrorCode.SLUG_TAKEN);
        }
        applyCategory(c, req, clock.instant());
        record(admin, "CATEGORY_UPDATED", "CATEGORY", id, Map.of("slug", c.getSlug(), "active", c.isActive()), ip);
        return categoryService.adminTree();
    }

    @Transactional
    public List<CategoryView> deleteCategory(AuthenticatedUser admin, long id, String ip) {
        Category c = categories.findById(id).orElseThrow(() -> ApiException.notFound("Category"));
        List<Subcategory> subs = subcategories.findByCategoryId(id);
        if (!subs.isEmpty() && organizations.existsBySubcategoryIdIn(subs.stream().map(Subcategory::getId).toList())) {
            throw new ApiException(ErrorCode.CATEGORY_IN_USE);
        }
        requirements.deleteAll(requirements.findByCategoryIdOrderBySortOrderAscIdAsc(id));
        subcategories.deleteAll(subs);
        categories.delete(c);
        record(admin, "CATEGORY_DELETED", "CATEGORY", id, Map.of("slug", c.getSlug()), ip);
        return categoryService.adminTree();
    }

    // ----- subcategories -----

    @Transactional
    public List<CategoryView> createSubcategory(AuthenticatedUser admin, long categoryId, SubcategoryRequest req,
                                                String ip) {
        categories.findById(categoryId).orElseThrow(() -> ApiException.notFound("Category"));
        if (subcategories.existsByCategoryIdAndSlug(categoryId, req.slug())) {
            throw new ApiException(ErrorCode.SLUG_TAKEN);
        }
        Instant now = clock.instant();
        Subcategory s = new Subcategory(categoryId, now);
        applySubcategory(s, req, now);
        subcategories.save(s);
        record(admin, "SUBCATEGORY_CREATED", "SUBCATEGORY", s.getId(), Map.of("categoryId", categoryId,
                "slug", s.getSlug()), ip);
        return categoryService.adminTree();
    }

    @Transactional
    public List<CategoryView> updateSubcategory(AuthenticatedUser admin, long categoryId, long id,
                                                SubcategoryRequest req, String ip) {
        Subcategory s = subcategory(categoryId, id);
        if (subcategories.existsByCategoryIdAndSlugAndIdNot(categoryId, req.slug(), id)) {
            throw new ApiException(ErrorCode.SLUG_TAKEN);
        }
        applySubcategory(s, req, clock.instant());
        record(admin, "SUBCATEGORY_UPDATED", "SUBCATEGORY", id, Map.of("slug", s.getSlug(), "active", s.isActive()), ip);
        return categoryService.adminTree();
    }

    @Transactional
    public List<CategoryView> deleteSubcategory(AuthenticatedUser admin, long categoryId, long id, String ip) {
        Subcategory s = subcategory(categoryId, id);
        if (organizations.existsBySubcategoryIdIn(List.of(id))) {
            throw new ApiException(ErrorCode.CATEGORY_IN_USE);
        }
        subcategories.delete(s);
        record(admin, "SUBCATEGORY_DELETED", "SUBCATEGORY", id, Map.of("slug", s.getSlug()), ip);
        return categoryService.adminTree();
    }

    // ----- document requirements -----

    @Transactional
    public List<CategoryView> createRequirement(AuthenticatedUser admin, long categoryId, RequirementRequest req,
                                                String ip) {
        categories.findById(categoryId).orElseThrow(() -> ApiException.notFound("Category"));
        if (requirements.existsByCategoryIdAndCode(categoryId, req.code())) {
            throw ApiException.invalidField("code", "DUPLICATE", "This code already exists in the category");
        }
        Instant now = clock.instant();
        DocumentRequirement r = new DocumentRequirement(categoryId, now);
        applyRequirement(r, req, now);
        requirements.save(r);
        record(admin, "DOC_REQUIREMENT_CREATED", "DOC_REQUIREMENT", r.getId(), Map.of("categoryId", categoryId,
                "code", r.getCode(), "mandatory", r.isMandatory()), ip);
        return categoryService.adminTree();
    }

    @Transactional
    public List<CategoryView> updateRequirement(AuthenticatedUser admin, long categoryId, long id,
                                                RequirementRequest req, String ip) {
        DocumentRequirement r = requirement(categoryId, id);
        if (requirements.existsByCategoryIdAndCodeAndIdNot(categoryId, req.code(), id)) {
            throw ApiException.invalidField("code", "DUPLICATE", "This code already exists in the category");
        }
        applyRequirement(r, req, clock.instant());
        record(admin, "DOC_REQUIREMENT_UPDATED", "DOC_REQUIREMENT", id, Map.of("code", r.getCode(),
                "mandatory", r.isMandatory(), "active", r.isActive()), ip);
        return categoryService.adminTree();
    }

    @Transactional
    public List<CategoryView> deleteRequirement(AuthenticatedUser admin, long categoryId, long id, String ip) {
        DocumentRequirement r = requirement(categoryId, id);
        if (organizationDocuments.existsByRequirementId(id)) {
            throw new ApiException(ErrorCode.CATEGORY_IN_USE);
        }
        requirements.delete(r);
        record(admin, "DOC_REQUIREMENT_DELETED", "DOC_REQUIREMENT", id, Map.of("code", r.getCode()), ip);
        return categoryService.adminTree();
    }

    // ----- helpers -----

    private Subcategory subcategory(long categoryId, long id) {
        return subcategories.findById(id).filter(s -> s.getCategoryId() == categoryId)
                .orElseThrow(() -> ApiException.notFound("Subcategory"));
    }

    private DocumentRequirement requirement(long categoryId, long id) {
        return requirements.findById(id).filter(r -> r.getCategoryId() == categoryId)
                .orElseThrow(() -> ApiException.notFound("Document requirement"));
    }

    private static void applyCategory(Category c, CategoryRequest req, Instant now) {
        c.update(req.slug(), LocalizedText.clean("name", req.name(), 80, true),
                LocalizedText.clean("description", req.description(), 300, false), req.icon(),
                req.bookingWorkflow(), req.active() == null || req.active(),
                req.sortOrder() == null ? 0 : req.sortOrder(), now);
    }

    private static void applySubcategory(Subcategory s, SubcategoryRequest req, Instant now) {
        s.update(req.slug(), LocalizedText.clean("name", req.name(), 80, true), req.active() == null || req.active(),
                req.sortOrder() == null ? 0 : req.sortOrder(), now);
    }

    private static void applyRequirement(DocumentRequirement r, RequirementRequest req, Instant now) {
        Set<String> types = new LinkedHashSet<>(req.allowedTypes());
        if (!DOCUMENT_TYPES.containsAll(types)) {
            throw ApiException.invalidField("allowedTypes", "UNSUPPORTED_TYPE",
                    "Allowed types are PDF, JPEG, PNG and WebP");
        }
        r.update(req.code(), LocalizedText.clean("label", req.label(), 150, true),
                LocalizedText.clean("description", req.description(), 500, false),
                req.mandatory() == null || req.mandatory(), types, req.maxSizeBytes(),
                req.active() == null || req.active(), req.sortOrder() == null ? 0 : req.sortOrder(), now);
    }

    private void record(AuthenticatedUser admin, String action, String type, Long id, Map<String, ?> details,
                        String ip) {
        audit.record(admin.id(), admin.primaryRole(), action, type, String.valueOf(id), details, ip);
    }
}
