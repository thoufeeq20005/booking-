package com.bookeasy.catalog;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.bookeasy.catalog.CatalogDtos.BranchOffer;
import com.bookeasy.catalog.CatalogDtos.ServiceRequest;
import com.bookeasy.catalog.CatalogDtos.ServiceView;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.file.ContentSniffer;
import com.bookeasy.file.FilePurpose;
import com.bookeasy.file.FileService;
import com.bookeasy.file.FileService.UploadRules;
import com.bookeasy.file.FileVisibility;
import com.bookeasy.file.UploadedFile;
import com.bookeasy.file.UploadedFileRepository;
import com.bookeasy.organization.entity.Branch;
import com.bookeasy.organization.entity.Organization;
import com.bookeasy.organization.repository.BranchRepository;
import com.bookeasy.organization.service.OrganizationAccess;

/** Owner management of an organization's services. */
@Service
public class CatalogService {

    static final int MAX_SERVICES = 200;
    static final UploadRules IMAGE_RULES = new UploadRules(
            Set.of(ContentSniffer.PNG, ContentSniffer.JPEG, ContentSniffer.WEBP), 5L * 1024 * 1024);

    private final ServiceRepository services;
    private final BranchServiceRepository branchServices;
    private final BranchRepository branches;
    private final OrganizationAccess access;
    private final Clock clock;

    private final FileService fileService;
    private final UploadedFileRepository files;

    public CatalogService(ServiceRepository services, BranchServiceRepository branchServices, BranchRepository branches,
                          OrganizationAccess access, Clock clock, FileService fileService, UploadedFileRepository files) {
        this.fileService = fileService;
        this.files = files;
        this.services = services;
        this.branchServices = branchServices;
        this.branches = branches;
        this.access = access;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ServiceView> list(long userId, long organizationId) {
        Organization org = access.owned(userId, organizationId);
        return views(services.findByOrganizationIdOrderByNameAsc(org.getId()));
    }

    @Transactional
    public ServiceView create(long userId, long organizationId, ServiceRequest req) {
        Organization org = access.ownedForOperationsEdit(userId, organizationId);
        if (services.findByOrganizationIdOrderByNameAsc(org.getId()).size() >= MAX_SERVICES) {
            throw ApiException.invalidField("name", "TOO_MANY", "This business has the maximum number of services");
        }
        ServiceOffering s = new ServiceOffering(org.getId(), clock.instant());
        apply(org, s, req);
        return views(List.of(s)).get(0);
    }

    @Transactional
    public ServiceView update(long userId, long organizationId, long serviceId, ServiceRequest req) {
        Organization org = access.ownedForOperationsEdit(userId, organizationId);
        ServiceOffering s = services.findByIdAndOrganizationId(serviceId, org.getId())
                .orElseThrow(() -> ApiException.notFound("Service"));
        apply(org, s, req);
        return views(List.of(s)).get(0);
    }

    /** Deletes a service. Once bookings exist (Phase 7) services with bookings will only be deactivated. */
    @Transactional
    public void delete(long userId, long organizationId, long serviceId) {
        Organization org = access.ownedForOperationsEdit(userId, organizationId);
        ServiceOffering s = services.findByIdAndOrganizationId(serviceId, org.getId())
                .orElseThrow(() -> ApiException.notFound("Service"));
        Long image = s.getImageFileId();
        services.delete(s);
        if (image != null) {
            services.flush();
            files.findById(image).ifPresent(fileService::delete);
        }
    }

    /** Replaces the photo shown next to the service on the business page. */
    @Transactional
    public ServiceView setImage(long userId, long organizationId, long serviceId, MultipartFile upload) {
        Organization org = access.ownedForOperationsEdit(userId, organizationId);
        ServiceOffering s = services.findByIdAndOrganizationId(serviceId, org.getId())
                .orElseThrow(() -> ApiException.notFound("Service"));
        UploadedFile stored = fileService.store(upload, IMAGE_RULES, FileVisibility.PUBLIC, FilePurpose.SERVICE_IMAGE,
                userId);
        Long previous = s.getImageFileId();
        s.setImageFileId(stored.getId(), clock.instant());
        services.saveAndFlush(s);
        if (previous != null) {
            files.findById(previous).ifPresent(fileService::delete);
        }
        return views(List.of(s)).get(0);
    }

    @Transactional
    public ServiceView removeImage(long userId, long organizationId, long serviceId) {
        Organization org = access.ownedForOperationsEdit(userId, organizationId);
        ServiceOffering s = services.findByIdAndOrganizationId(serviceId, org.getId())
                .orElseThrow(() -> ApiException.notFound("Service"));
        Long previous = s.getImageFileId();
        if (previous != null) {
            s.setImageFileId(null, clock.instant());
            services.saveAndFlush(s);
            files.findById(previous).ifPresent(fileService::delete);
        }
        return views(List.of(s)).get(0);
    }

    private void apply(Organization org, ServiceOffering s, ServiceRequest req) {
        Set<Long> orgBranches = branches.findByOrganizationIdOrderByIdAsc(org.getId()).stream().map(Branch::getId)
                .collect(Collectors.toSet());
        Set<Long> seen = new HashSet<>();
        for (BranchOffer b : req.branches()) {
            if (!orgBranches.contains(b.branchId()) || !seen.add(b.branchId())) {
                throw ApiException.invalidField("branches", "INVALID_BRANCH", "Choose this business's branches");
            }
        }
        Instant now = clock.instant();
        s.update(req.name().strip(), blank(req.description()), req.durationMinutes(),
                req.price().setScale(2, RoundingMode.HALF_UP),
                req.consultationType() == null ? ServiceOffering.ConsultationType.IN_PERSON : req.consultationType(),
                req.requiresStaff() == null || req.requiresStaff(), or(req.bufferMinutes(), 0),
                or(req.slotIntervalMinutes(), 15), or(req.maxAdvanceDays(), 60), or(req.minNoticeMinutes(), 60),
                req.active() == null || req.active(), now);
        services.saveAndFlush(s);
        branchServices.deleteAllForService(s.getId());
        branchServices.saveAll(req.branches().stream()
                .map(b -> new BranchService(b.branchId(), s.getId(),
                        b.priceOverride() == null ? null : b.priceOverride().setScale(2, RoundingMode.HALF_UP)))
                .toList());
        branchServices.flush();
    }

    private List<ServiceView> views(List<ServiceOffering> list) {
        Map<Long, List<BranchOffer>> offers = branchServices.findByServiceIdIn(list.stream().map(ServiceOffering::getId)
                        .toList()).stream()
                .collect(Collectors.groupingBy(BranchService::getServiceId, Collectors.mapping(
                        b -> new BranchOffer(b.getBranchId(), b.getPriceOverride()), Collectors.toList())));
        Map<Long, String> images = files.findAllById(list.stream().map(ServiceOffering::getImageFileId)
                        .filter(java.util.Objects::nonNull).toList()).stream()
                .filter(f -> f.publicUrl() != null)
                .collect(Collectors.toMap(UploadedFile::getId, UploadedFile::publicUrl));
        return list.stream().map(s -> ServiceView.of(s, offers.getOrDefault(s.getId(), List.of()),
                s.getImageFileId() == null ? null : images.get(s.getImageFileId()))).toList();
    }

    private static int or(Integer v, int fallback) {
        return v == null ? fallback : v;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
