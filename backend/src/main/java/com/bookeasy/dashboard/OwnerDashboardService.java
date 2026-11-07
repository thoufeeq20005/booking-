package com.bookeasy.dashboard;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.category.entity.Subcategory;
import com.bookeasy.category.repository.SubcategoryRepository;
import com.bookeasy.organization.dto.OrganizationDtos.DocumentSlot;
import com.bookeasy.organization.dto.OrganizationDtos.MissingStep;
import com.bookeasy.organization.dto.OrganizationDtos.OrganizationSummary;
import com.bookeasy.organization.entity.Branch;
import com.bookeasy.organization.entity.Organization;
import com.bookeasy.organization.entity.OrganizationDocument;
import com.bookeasy.organization.entity.OrganizationStatus;
import com.bookeasy.organization.repository.BranchRepository;
import com.bookeasy.organization.repository.HolidayRepository;
import com.bookeasy.organization.repository.OrganizationRepository;
import com.bookeasy.organization.service.OrganizationViews;

/** The business owner's overview: every business they own, how far its setup is, and upcoming closures. */
@Service
public class OwnerDashboardService {

    private final OrganizationRepository organizations;
    private final BranchRepository branches;
    private final HolidayRepository holidays;
    private final SubcategoryRepository subcategories;
    private final OrganizationViews views;
    private final Clock clock;

    public OwnerDashboardService(OrganizationRepository organizations, BranchRepository branches,
                                 HolidayRepository holidays, SubcategoryRepository subcategories,
                                 OrganizationViews views, Clock clock) {
        this.organizations = organizations;
        this.branches = branches;
        this.holidays = holidays;
        this.subcategories = subcategories;
        this.views = views;
        this.clock = clock;
    }

    public record DocumentProgress(int required, int uploaded, int accepted, int rejected) {
    }

    public record BusinessCard(OrganizationSummary summary, List<MissingStep> missingSteps, int activeBranches,
                               int branchesWithoutHours, DocumentProgress documents) {
    }

    public record UpcomingHoliday(long organizationId, String organizationName, long branchId, String branchName,
                                  LocalDate date, String reason) {
    }

    public record OwnerDashboard(Map<OrganizationStatus, Long> byStatus, List<BusinessCard> businesses,
                                 List<UpcomingHoliday> upcomingHolidays) {
    }

    @Transactional(readOnly = true)
    public OwnerDashboard overview(long userId) {
        List<Organization> orgs = organizations.findOwnedBy(userId);
        Map<Long, OrganizationSummary> summaries = new HashMap<>();
        views.summaries(orgs, false).forEach(s -> summaries.put(s.id(), s));

        Map<OrganizationStatus, Long> byStatus = new EnumMap<>(OrganizationStatus.class);
        List<BusinessCard> cards = new java.util.ArrayList<>();
        List<Branch> allBranches = branches.findByOrganizationIdInOrderByIdAsc(orgs.stream().map(Organization::getId).toList());
        var hoursByBranch = views.hoursByBranch(allBranches);

        for (Organization org : orgs) {
            byStatus.merge(org.getStatus(), 1L, Long::sum);
            List<Branch> mine = allBranches.stream().filter(b -> b.getOrganizationId().equals(org.getId())
                    && b.isActive()).toList();
            int withoutHours = (int) mine.stream().filter(b -> !hoursByBranch.containsKey(b.getId())).count();
            Long categoryId = subcategories.findById(org.getSubcategoryId()).map(Subcategory::getCategoryId)
                    .orElse(null);
            List<DocumentSlot> required = views.documentSlots(org, categoryId).stream()
                    .filter(s -> s.requirement().active() && s.requirement().mandatory()).toList();
            DocumentProgress docs = new DocumentProgress(required.size(),
                    (int) required.stream().filter(s -> s.document() != null).count(),
                    countStatus(required, OrganizationDocument.Status.ACCEPTED),
                    countStatus(required, OrganizationDocument.Status.REJECTED));
            cards.add(new BusinessCard(summaries.get(org.getId()), views.missingSteps(org), mine.size(), withoutHours,
                    docs));
        }

        Map<Long, Branch> branchById = new HashMap<>();
        allBranches.forEach(b -> branchById.put(b.getId(), b));
        Map<Long, String> orgNames = new HashMap<>();
        orgs.forEach(o -> orgNames.put(o.getId(), o.getName()));
        // Every branch uses the platform timezone today (it can't be changed in the UI yet).
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Kolkata"));
        List<UpcomingHoliday> upcoming = allBranches.isEmpty() ? List.of()
                : holidays.findTop10ByBranchIdInAndDateGreaterThanEqualOrderByDateAsc(
                        allBranches.stream().map(Branch::getId).toList(), today).stream()
                .map(h -> {
                    Branch b = branchById.get(h.getBranchId());
                    return new UpcomingHoliday(b.getOrganizationId(), orgNames.get(b.getOrganizationId()), b.getId(),
                            b.getName(), h.getDate(), h.getReason());
                }).toList();

        return new OwnerDashboard(byStatus, cards, upcoming);
    }

    private static int countStatus(List<DocumentSlot> slots, OrganizationDocument.Status status) {
        return (int) slots.stream().filter(s -> s.document() != null && s.document().status() == status).count();
    }
}
