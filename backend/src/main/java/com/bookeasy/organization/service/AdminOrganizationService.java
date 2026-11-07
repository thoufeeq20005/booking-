package com.bookeasy.organization.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.bookeasy.audit.AuditService;
import com.bookeasy.category.entity.Subcategory;
import com.bookeasy.category.repository.SubcategoryRepository;
import com.bookeasy.common.api.PageResponse;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.bookeasy.common.security.AuthenticatedUser;
import com.bookeasy.file.UploadedFile;
import com.bookeasy.file.UploadedFileRepository;
import com.bookeasy.organization.dto.OrganizationDtos.DocumentReviewRequest;
import com.bookeasy.organization.dto.OrganizationDtos.DocumentSlot;
import com.bookeasy.organization.dto.OrganizationDtos.OrganizationDetail;
import com.bookeasy.organization.dto.OrganizationDtos.OrganizationSummary;
import com.bookeasy.organization.entity.Organization;
import com.bookeasy.organization.entity.OrganizationDocument;
import com.bookeasy.organization.entity.OrganizationStatus;
import com.bookeasy.organization.entity.OrganizationVerification;
import com.bookeasy.organization.entity.OrganizationVerification.Action;
import com.bookeasy.organization.repository.OrganizationDocumentRepository;
import com.bookeasy.organization.repository.OrganizationOwnerRepository;
import com.bookeasy.organization.repository.OrganizationRepository;
import com.bookeasy.organization.repository.OrganizationVerificationRepository;

/** Super Admin review queue: document checks and approve / reject / suspend / reinstate. Every decision is audited. */
@Service
public class AdminOrganizationService {

    private final OrganizationRepository organizations;
    private final OrganizationOwnerRepository owners;
    private final OrganizationDocumentRepository documents;
    private final OrganizationVerificationRepository verifications;
    private final SubcategoryRepository subcategories;
    private final UploadedFileRepository files;
    private final OrganizationViews views;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AdminOrganizationService(OrganizationRepository organizations, OrganizationOwnerRepository owners,
                                    OrganizationDocumentRepository documents,
                                    OrganizationVerificationRepository verifications,
                                    SubcategoryRepository subcategories, UploadedFileRepository files,
                                    OrganizationViews views, AuditService audit, ApplicationEventPublisher events,
                                    Clock clock) {
        this.organizations = organizations;
        this.owners = owners;
        this.documents = documents;
        this.verifications = verifications;
        this.subcategories = subcategories;
        this.files = files;
        this.views = views;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    /** The pending queue is oldest-first (fair review order); other lists show the latest changes first. */
    @Transactional(readOnly = true)
    public PageResponse<OrganizationSummary> search(String q, OrganizationStatus status, int page, int size) {
        Sort sort = status == OrganizationStatus.PENDING
                ? Sort.by(Sort.Direction.ASC, "submittedAt").and(Sort.by("id"))
                : Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by(Sort.Direction.DESC, "id"));
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort);
        var result = organizations.search(StringUtils.hasText(q) ? q.trim() : null, status, pageable);
        var summaries = views.summaries(result.getContent(), true);
        return new PageResponse<>(summaries, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public OrganizationDetail detail(long organizationId) {
        return views.detail(find(organizationId), true);
    }

    @Transactional(readOnly = true)
    public UploadedFile documentFile(long organizationId, long documentId) {
        OrganizationDocument doc = documents.findByIdAndOrganizationId(documentId, organizationId)
                .orElseThrow(() -> ApiException.notFound("Document"));
        return files.findById(doc.getFileId()).orElseThrow(() -> ApiException.notFound("Document"));
    }

    /** Accept or reject one document while the application is under review. Rejections need a note. */
    @Transactional
    public OrganizationDetail reviewDocument(AuthenticatedUser admin, long organizationId, long documentId,
                                             DocumentReviewRequest req, String ip) {
        Organization org = lockForReview(admin, organizationId);
        if (org.getStatus() != OrganizationStatus.PENDING) {
            throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        if (req.status() == OrganizationDocument.Status.SUBMITTED) {
            throw ApiException.invalidField("status", "INVALID", "Choose ACCEPTED or REJECTED");
        }
        String note = Inputs.text(req.note());
        if (req.status() == OrganizationDocument.Status.REJECTED && note == null) {
            throw ApiException.invalidField("note", "NotBlank", "Explain what is wrong with the document");
        }
        OrganizationDocument doc = documents.findByIdAndOrganizationId(documentId, org.getId())
                .orElseThrow(() -> ApiException.notFound("Document"));
        Instant now = clock.instant();
        doc.review(req.status(), note, admin.id(), now);
        org.touch(now);
        audit.record(admin.id(), admin.primaryRole(), "ORGANIZATION_DOCUMENT_REVIEWED", "ORGANIZATION",
                String.valueOf(org.getId()), details("documentId", doc.getId(), "status", req.status().name(),
                        "note", note), ip);
        return views.detail(org, true);
    }

    /**
     * Approves a pending application. Every active mandatory document must be present and not rejected;
     * documents not yet reviewed individually are accepted with the approval.
     */
    @Transactional
    public OrganizationDetail approve(AuthenticatedUser admin, long organizationId, String note, String ip) {
        Organization org = lockForReview(admin, organizationId);
        if (org.getStatus() != OrganizationStatus.PENDING) {
            throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        Long categoryId = subcategories.findById(org.getSubcategoryId()).map(Subcategory::getCategoryId).orElse(null);
        boolean blocked = views.documentSlots(org, categoryId).stream()
                .filter(s -> s.requirement().active() && s.requirement().mandatory())
                .map(DocumentSlot::document)
                .anyMatch(d -> d == null || d.status() == OrganizationDocument.Status.REJECTED);
        if (blocked) {
            throw new ApiException(ErrorCode.DOCUMENTS_NOT_ACCEPTED);
        }
        Instant now = clock.instant();
        for (OrganizationDocument doc : documents.findByOrganizationId(org.getId())) {
            if (doc.getStatus() == OrganizationDocument.Status.SUBMITTED) {
                doc.review(OrganizationDocument.Status.ACCEPTED, null, admin.id(), now);
            }
        }
        return transition(admin, org, Action.APPROVED, Inputs.text(note), ip, () -> org.approve(now));
    }

    @Transactional
    public OrganizationDetail reject(AuthenticatedUser admin, long organizationId, String reason, String ip) {
        Organization org = lockForReview(admin, organizationId);
        String why = requireReason(reason);
        return transition(admin, org, Action.REJECTED, why, ip, () -> org.reject(why, clock.instant()));
    }

    @Transactional
    public OrganizationDetail suspend(AuthenticatedUser admin, long organizationId, String reason, String ip) {
        Organization org = lockForReview(admin, organizationId);
        String why = requireReason(reason);
        return transition(admin, org, Action.SUSPENDED, why, ip, () -> org.suspend(why, clock.instant()));
    }

    @Transactional
    public OrganizationDetail reinstate(AuthenticatedUser admin, long organizationId, String note, String ip) {
        Organization org = lockForReview(admin, organizationId);
        return transition(admin, org, Action.REINSTATED, Inputs.text(note), ip, () -> org.reinstate(clock.instant()));
    }

    private OrganizationDetail transition(AuthenticatedUser admin, Organization org, Action action, String reason,
                                          String ip, Runnable change) {
        OrganizationStatus from = org.getStatus();
        change.run();
        Instant now = clock.instant();
        verifications.save(new OrganizationVerification(org.getId(), action, from, org.getStatus(), reason,
                admin.id(), now));
        audit.record(admin.id(), admin.primaryRole(), "ORGANIZATION_" + action.name(), "ORGANIZATION",
                String.valueOf(org.getId()), details("from", from.name(), "to", org.getStatus().name(),
                        "reason", reason), ip);
        events.publishEvent(new OrganizationStatusChanged(org.getId(), action, reason));
        return views.detail(org, true);
    }

    /** Reviewers may not decide on a business they own themselves. */
    private Organization lockForReview(AuthenticatedUser admin, long organizationId) {
        Organization org = organizations.findByIdForUpdate(organizationId)
                .orElseThrow(OrganizationAccess::notFound);
        if (owners.existsByOrganizationIdAndUserId(org.getId(), admin.id())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "You cannot review your own business");
        }
        return org;
    }

    private Organization find(long organizationId) {
        return organizations.findById(organizationId).orElseThrow(OrganizationAccess::notFound);
    }

    private static String requireReason(String reason) {
        String why = Inputs.text(reason);
        if (why == null || why.length() < 5) {
            throw ApiException.invalidField("reason", "NotBlank", "Give the business a reason");
        }
        return why;
    }

    private static Map<String, Object> details(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            if (kv[i + 1] != null) {
                m.put((String) kv[i], kv[i + 1]);
            }
        }
        return m;
    }
}
