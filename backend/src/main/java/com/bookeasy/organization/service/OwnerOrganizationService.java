package com.bookeasy.organization.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.bookeasy.audit.AuditService;
import com.bookeasy.category.entity.Category;
import com.bookeasy.category.entity.Subcategory;
import com.bookeasy.category.repository.CategoryRepository;
import com.bookeasy.category.repository.SubcategoryRepository;
import com.bookeasy.common.api.ApiError;
import com.bookeasy.common.crypto.CryptoService;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.bookeasy.common.text.Slugs;
import com.bookeasy.file.ContentSniffer;
import com.bookeasy.file.FilePurpose;
import com.bookeasy.file.FileService;
import com.bookeasy.file.FileService.UploadRules;
import com.bookeasy.file.FileVisibility;
import com.bookeasy.file.UploadedFile;
import com.bookeasy.file.UploadedFileRepository;
import com.bookeasy.organization.dto.OrganizationDtos.MissingStep;
import com.bookeasy.organization.dto.OrganizationDtos.OrganizationDetail;
import com.bookeasy.organization.dto.OrganizationDtos.OrganizationRequest;
import com.bookeasy.organization.dto.OrganizationDtos.OrganizationSummary;
import com.bookeasy.organization.entity.ConfirmationMode;
import com.bookeasy.organization.entity.Organization;
import com.bookeasy.organization.entity.OrganizationOwner;
import com.bookeasy.organization.entity.OrganizationStatus;
import com.bookeasy.organization.entity.OrganizationVerification;
import com.bookeasy.organization.repository.OrganizationOwnerRepository;
import com.bookeasy.organization.repository.OrganizationRepository;
import com.bookeasy.organization.repository.OrganizationVerificationRepository;
import com.bookeasy.user.entity.RoleCode;
import com.bookeasy.user.entity.User;
import com.bookeasy.user.repository.UserRepository;

/** Business registration and the owner's own organization profile. */
@Service
public class OwnerOrganizationService {

    /** Unfinished applications (draft, pending, rejected) one person may have at a time. */
    static final int MAX_IN_PROGRESS = 3;
    static final UploadRules LOGO_RULES = new UploadRules(
            Set.of(ContentSniffer.PNG, ContentSniffer.JPEG, ContentSniffer.WEBP), 2L * 1024 * 1024);
    static final UploadRules PHOTO_RULES = new UploadRules(
            Set.of(ContentSniffer.PNG, ContentSniffer.JPEG, ContentSniffer.WEBP), 5L * 1024 * 1024);

    private final OrganizationRepository organizations;
    private final OrganizationOwnerRepository owners;
    private final OrganizationVerificationRepository verifications;
    private final SubcategoryRepository subcategories;
    private final CategoryRepository categories;
    private final UserRepository users;
    private final OrganizationAccess access;
    private final OrganizationViews views;
    private final OrganizationDocumentService documentService;
    private final FileService fileService;
    private final UploadedFileRepository files;
    private final AuditService audit;
    private final CryptoService crypto;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public OwnerOrganizationService(OrganizationRepository organizations, OrganizationOwnerRepository owners,
                                    OrganizationVerificationRepository verifications,
                                    SubcategoryRepository subcategories, CategoryRepository categories,
                                    UserRepository users, OrganizationAccess access, OrganizationViews views,
                                    OrganizationDocumentService documentService, FileService fileService, UploadedFileRepository files, AuditService audit,
                                    CryptoService crypto, ApplicationEventPublisher events, Clock clock) {
        this.organizations = organizations;
        this.owners = owners;
        this.verifications = verifications;
        this.subcategories = subcategories;
        this.categories = categories;
        this.users = users;
        this.access = access;
        this.views = views;
        this.documentService = documentService;
        this.fileService = fileService;
        this.files = files;
        this.audit = audit;
        this.crypto = crypto;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Starts a business application. Any signed-in person can do this; it makes them a BUSINESS_OWNER right away
     * (roles are re-read on every request), but nothing is public until a Super Admin approves the business.
     */
    @Transactional
    public OrganizationDetail create(long userId, OrganizationRequest req, String ip) {
        User user = users.findWithRolesById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        if (organizations.countOwnedInStatuses(userId, OrganizationStatus.IN_PROGRESS) >= MAX_IN_PROGRESS) {
            throw new ApiException(ErrorCode.ORGANIZATION_LIMIT_REACHED);
        }
        Subcategory sub = requireSelectableSubcategory(req.subcategoryId());
        Instant now = clock.instant();
        String name = Inputs.text(req.name());

        Organization org = new Organization(uniqueSlug(name), userId, now);
        org.updateIdentity(sub.getId(), name);
        applyOperations(org, req, now);
        organizations.save(org);
        owners.save(new OrganizationOwner(org.getId(), userId, OrganizationOwner.Role.PRIMARY, now));

        if (!user.hasRole(RoleCode.BUSINESS_OWNER)) {
            user.addRole(RoleCode.BUSINESS_OWNER);
            user.touch(now);
        }
        audit.record(userId, user.hasRole(RoleCode.SUPER_ADMIN) ? RoleCode.SUPER_ADMIN.name()
                        : RoleCode.BUSINESS_OWNER.name(), "ORGANIZATION_CREATED", "ORGANIZATION",
                String.valueOf(org.getId()), Map.of("name", name, "subcategoryId", sub.getId()), ip);
        return views.detail(org, false);
    }

    @Transactional(readOnly = true)
    public List<OrganizationSummary> listMine(long userId) {
        return views.summaries(organizations.findOwnedBy(userId), false);
    }

    @Transactional(readOnly = true)
    public OrganizationDetail detail(long userId, long organizationId) {
        return views.detail(access.owned(userId, organizationId), false);
    }

    /**
     * Before approval everything can change. After approval only operational details can; renaming or
     * re-categorising an approved business needs a new review, so it goes through support.
     */
    @Transactional
    public OrganizationDetail update(long userId, long organizationId, OrganizationRequest req) {
        Organization org = access.ownedForOperationsEdit(userId, organizationId);
        Instant now = clock.instant();
        String name = Inputs.text(req.name());
        if (org.getStatus().identityEditable()) {
            if (!Objects.equals(req.subcategoryId(), org.getSubcategoryId())) {
                Subcategory next = requireSelectableSubcategory(req.subcategoryId());
                Long currentCategory = subcategories.findById(org.getSubcategoryId())
                        .map(Subcategory::getCategoryId).orElse(null);
                if (!next.getCategoryId().equals(currentCategory)) {
                    documentService.removeOutsideCategory(org, next.getCategoryId());
                }
            }
            org.updateIdentity(req.subcategoryId(), name);
        } else if (!Objects.equals(name, org.getName()) || !Objects.equals(req.subcategoryId(), org.getSubcategoryId())) {
            throw new ApiException(ErrorCode.ORGANIZATION_NOT_EDITABLE);
        }
        applyOperations(org, req, now);
        return views.detail(org, false);
    }

    @Transactional
    public OrganizationDetail setLogo(long userId, long organizationId, MultipartFile upload) {
        Organization org = access.ownedForOperationsEdit(userId, organizationId);
        UploadedFile stored = fileService.store(upload, LOGO_RULES, FileVisibility.PUBLIC,
                FilePurpose.ORGANIZATION_LOGO, userId);
        Long previous = org.getLogoFileId();
        org.setLogoFileId(stored.getId(), clock.instant());
        if (previous != null) {
            files.findById(previous).ifPresent(fileService::delete);
        }
        return views.detail(org, false);
    }

    @Transactional
    public OrganizationDetail removeLogo(long userId, long organizationId) {
        Organization org = access.ownedForOperationsEdit(userId, organizationId);
        Long previous = org.getLogoFileId();
        if (previous != null) {
            org.setLogoFileId(null, clock.instant());
            organizations.flush();
            files.findById(previous).ifPresent(fileService::delete);
        }
        return views.detail(org, false);
    }

    /** Replaces the business's cover photo (the shop photo shown on search cards and the business page). */
    @Transactional
    public OrganizationDetail setCover(long userId, long organizationId, MultipartFile upload) {
        Organization org = access.ownedForOperationsEdit(userId, organizationId);
        UploadedFile stored = fileService.store(upload, PHOTO_RULES, FileVisibility.PUBLIC,
                FilePurpose.ORGANIZATION_COVER, userId);
        Long previous = org.getCoverFileId();
        org.setCoverFileId(stored.getId(), clock.instant());
        if (previous != null) {
            files.findById(previous).ifPresent(fileService::delete);
        }
        return views.detail(org, false);
    }

    @Transactional
    public OrganizationDetail removeCover(long userId, long organizationId) {
        Organization org = access.ownedForOperationsEdit(userId, organizationId);
        Long previous = org.getCoverFileId();
        if (previous != null) {
            org.setCoverFileId(null, clock.instant());
            organizations.flush();
            files.findById(previous).ifPresent(fileService::delete);
        }
        return views.detail(org, false);
    }

    /** Sends the application for review. Every missing step is reported at once. */
    @Transactional
    public OrganizationDetail submit(long userId, long organizationId, String ip) {
        Organization org = access.ownedForUpdate(userId, organizationId);
        if (!org.getStatus().identityEditable()) {
            throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        List<MissingStep> missing = views.missingSteps(org);
        if (!isSelectable(org.getSubcategoryId())) {
            throw ApiException.invalidField("subcategoryId", "INACTIVE", "This category is no longer available");
        }
        if (!missing.isEmpty()) {
            throw ApiException.withFields(ErrorCode.ORGANIZATION_INCOMPLETE, missing.stream()
                    .map(m -> new ApiError.FieldError(m.name(), m.name(), "Step incomplete")).toList());
        }
        OrganizationStatus from = org.getStatus();
        Instant now = clock.instant();
        org.submit(now);
        verifications.save(new OrganizationVerification(org.getId(), OrganizationVerification.Action.SUBMITTED, from,
                org.getStatus(), null, userId, now));
        audit.record(userId, RoleCode.BUSINESS_OWNER.name(), "ORGANIZATION_SUBMITTED", "ORGANIZATION",
                String.valueOf(org.getId()), Map.of("from", from.name()), ip);
        events.publishEvent(new OrganizationStatusChanged(org.getId(), OrganizationVerification.Action.SUBMITTED, null));
        return views.detail(org, false);
    }

    private void applyOperations(Organization org, OrganizationRequest req, Instant now) {
        org.updateOperations(Inputs.text(req.description()), Inputs.email(req.contactEmail()),
                Inputs.phone("contactPhone", req.contactPhone()), Inputs.website(req.website()),
                Inputs.gstin(req.gstin()),
                req.confirmationMode() == null ? ConfirmationMode.INSTANT : req.confirmationMode(), now);
    }

    private Subcategory requireSelectableSubcategory(Long subcategoryId) {
        Subcategory sub = subcategoryId == null ? null : subcategories.findById(subcategoryId).orElse(null);
        if (sub == null || !sub.isActive()
                || !categories.findById(sub.getCategoryId()).map(Category::isActive).orElse(false)) {
            throw ApiException.invalidField("subcategoryId", "INACTIVE", "Choose an available category");
        }
        return sub;
    }

    private boolean isSelectable(long subcategoryId) {
        return subcategories.findById(subcategoryId)
                .filter(Subcategory::isActive)
                .flatMap(s -> categories.findById(s.getCategoryId()))
                .map(Category::isActive)
                .orElse(false);
    }

    private String uniqueSlug(String name) {
        String base = Slugs.from(name, 60, "business");
        String slug = base;
        while (organizations.existsBySlug(slug)) {
            slug = base + "-" + crypto.randomToken(4).toLowerCase().replaceAll("[^a-z0-9]", "x");
        }
        return slug;
    }
}
