package com.bookeasy.organization.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.bookeasy.audit.AuditService;
import com.bookeasy.category.entity.DocumentRequirement;
import com.bookeasy.category.entity.Subcategory;
import com.bookeasy.category.repository.DocumentRequirementRepository;
import com.bookeasy.category.repository.SubcategoryRepository;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.file.FilePurpose;
import com.bookeasy.file.FileService;
import com.bookeasy.file.FileService.UploadRules;
import com.bookeasy.file.FileVisibility;
import com.bookeasy.file.UploadedFile;
import com.bookeasy.file.UploadedFileRepository;
import com.bookeasy.organization.dto.OrganizationDtos.DocumentSlot;
import com.bookeasy.organization.entity.Organization;
import com.bookeasy.organization.entity.OrganizationDocument;
import com.bookeasy.organization.repository.OrganizationDocumentRepository;
import com.bookeasy.user.entity.RoleCode;

/** Verification documents uploaded by owners. Files are PRIVATE: only owners and Super Admins can read them. */
@Service
public class OrganizationDocumentService {

    private final OrganizationDocumentRepository documents;
    private final DocumentRequirementRepository requirements;
    private final SubcategoryRepository subcategories;
    private final UploadedFileRepository files;
    private final FileService fileService;
    private final OrganizationAccess access;
    private final OrganizationViews views;
    private final AuditService audit;
    private final Clock clock;

    public OrganizationDocumentService(OrganizationDocumentRepository documents,
                                       DocumentRequirementRepository requirements,
                                       SubcategoryRepository subcategories, UploadedFileRepository files,
                                       FileService fileService, OrganizationAccess access, OrganizationViews views,
                                       AuditService audit, Clock clock) {
        this.documents = documents;
        this.requirements = requirements;
        this.subcategories = subcategories;
        this.files = files;
        this.fileService = fileService;
        this.access = access;
        this.views = views;
        this.audit = audit;
        this.clock = clock;
    }

    /** Uploads or replaces the document for one requirement. A replacement goes back to "submitted". */
    @Transactional
    public DocumentSlot upload(long userId, long organizationId, long requirementId, MultipartFile upload, String ip) {
        Organization org = access.ownedForIdentityEdit(userId, organizationId);
        DocumentRequirement req = requirementFor(org, requirementId);
        if (!req.isActive()) {
            throw ApiException.notFound("Document requirement");
        }
        UploadedFile stored = fileService.store(upload, new UploadRules(req.allowedTypes(), req.getMaxSizeBytes()),
                FileVisibility.PRIVATE, FilePurpose.ORGANIZATION_DOCUMENT, userId);
        Instant now = clock.instant();
        var existing = documents.findByOrganizationIdAndRequirementId(org.getId(), req.getId());
        if (existing.isPresent()) {
            OrganizationDocument doc = existing.get();
            Long oldFileId = doc.getFileId();
            doc.replaceFile(stored.getId(), now);
            documents.flush();
            files.findById(oldFileId).ifPresent(fileService::delete);
        } else {
            documents.save(new OrganizationDocument(org.getId(), req.getId(), stored.getId(), now));
        }
        org.touch(now);
        audit.record(userId, RoleCode.BUSINESS_OWNER.name(), "ORGANIZATION_DOCUMENT_UPLOADED", "ORGANIZATION",
                String.valueOf(org.getId()), Map.of("requirement", req.getCode(), "sha256", stored.getSha256(),
                        "sizeBytes", stored.getSizeBytes()), ip);
        return slot(org, req.getId());
    }

    @Transactional
    public void delete(long userId, long organizationId, long requirementId) {
        Organization org = access.ownedForIdentityEdit(userId, organizationId);
        OrganizationDocument doc = documents.findByOrganizationIdAndRequirementId(org.getId(), requirementId)
                .orElseThrow(() -> ApiException.notFound("Document"));
        Long fileId = doc.getFileId();
        documents.delete(doc);
        documents.flush();
        files.findById(fileId).ifPresent(fileService::delete);
        org.touch(clock.instant());
    }

    /** After a category change, drops documents that answered the previous category's requirements. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void removeOutsideCategory(Organization org, long categoryId) {
        java.util.Set<Long> keep = requirements.findByCategoryIdOrderBySortOrderAscIdAsc(categoryId).stream()
                .map(DocumentRequirement::getId).collect(java.util.stream.Collectors.toSet());
        for (OrganizationDocument doc : documents.findByOrganizationId(org.getId())) {
            if (!keep.contains(doc.getRequirementId())) {
                Long fileId = doc.getFileId();
                documents.delete(doc);
                documents.flush();
                files.findById(fileId).ifPresent(fileService::delete);
            }
        }
    }

    /** The owner's own copy of an uploaded document. */
    @Transactional(readOnly = true)
    public UploadedFile file(long userId, long organizationId, long requirementId) {
        Organization org = access.owned(userId, organizationId);
        OrganizationDocument doc = documents.findByOrganizationIdAndRequirementId(org.getId(), requirementId)
                .orElseThrow(() -> ApiException.notFound("Document"));
        return files.findById(doc.getFileId()).orElseThrow(() -> ApiException.notFound("Document"));
    }

    /** The requirement must belong to the organization's current category. */
    private DocumentRequirement requirementFor(Organization org, long requirementId) {
        Long categoryId = subcategories.findById(org.getSubcategoryId()).map(Subcategory::getCategoryId).orElse(null);
        return requirements.findById(requirementId)
                .filter(r -> r.getCategoryId().equals(categoryId))
                .orElseThrow(() -> ApiException.notFound("Document requirement"));
    }

    private DocumentSlot slot(Organization org, long requirementId) {
        Long categoryId = subcategories.findById(org.getSubcategoryId()).map(Subcategory::getCategoryId).orElse(null);
        return views.documentSlots(org, categoryId).stream()
                .filter(s -> s.requirement().id() == requirementId).findFirst()
                .orElseThrow(() -> ApiException.notFound("Document requirement"));
    }
}
