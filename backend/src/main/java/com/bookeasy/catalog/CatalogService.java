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

import com.bookeasy.catalog.CatalogDtos.BranchOffer;
import com.bookeasy.catalog.CatalogDtos.ServiceRequest;
import com.bookeasy.catalog.CatalogDtos.ServiceView;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.organization.entity.Branch;
import com.bookeasy.organization.entity.Organization;
import com.bookeasy.organization.repository.BranchRepository;
import com.bookeasy.organization.service.OrganizationAccess;

/** Owner management of an organization's services. */
@Service
public class CatalogService {

    static final int MAX_SERVICES = 200;

    private final ServiceRepository services;
    private final BranchServiceRepository branchServices;
    private final BranchRepository branches;
    private final OrganizationAccess access;
    private final Clock clock;

    public CatalogService(ServiceRepository services, BranchServiceRepository branchServices, BranchRepository branches,
                          OrganizationAccess access, Clock clock) {
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
        services.delete(services.findByIdAndOrganizationId(serviceId, org.getId())
                .orElseThrow(() -> ApiException.notFound("Service")));
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
        return list.stream().map(s -> ServiceView.of(s, offers.getOrDefault(s.getId(), List.of()))).toList();
    }

    private static int or(Integer v, int fallback) {
        return v == null ? fallback : v;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
