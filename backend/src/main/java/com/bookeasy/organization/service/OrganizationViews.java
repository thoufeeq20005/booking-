package com.bookeasy.organization.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.bookeasy.category.dto.CategoryDtos.RequirementView;
import com.bookeasy.category.entity.Category;
import com.bookeasy.category.entity.DocumentRequirement;
import com.bookeasy.category.entity.Subcategory;
import com.bookeasy.category.repository.CategoryRepository;
import com.bookeasy.category.repository.DocumentRequirementRepository;
import com.bookeasy.category.repository.SubcategoryRepository;
import com.bookeasy.file.UploadedFile;
import com.bookeasy.file.UploadedFileRepository;
import com.bookeasy.organization.dto.OrganizationDtos.BranchView;
import com.bookeasy.organization.dto.OrganizationDtos.DayHoursView;
import com.bookeasy.organization.dto.OrganizationDtos.DocumentSlot;
import com.bookeasy.organization.dto.OrganizationDtos.DocumentView;
import com.bookeasy.organization.dto.OrganizationDtos.Editable;
import com.bookeasy.organization.dto.OrganizationDtos.HistoryEntry;
import com.bookeasy.organization.dto.OrganizationDtos.MissingStep;
import com.bookeasy.organization.dto.OrganizationDtos.OrganizationDetail;
import com.bookeasy.organization.dto.OrganizationDtos.OrganizationSummary;
import com.bookeasy.organization.dto.OrganizationDtos.OwnerView;
import com.bookeasy.organization.entity.Branch;
import com.bookeasy.organization.entity.Organization;
import com.bookeasy.organization.entity.OrganizationDocument;
import com.bookeasy.organization.entity.OrganizationOwner;
import com.bookeasy.organization.entity.WorkingHours;
import com.bookeasy.organization.repository.BranchRepository;
import com.bookeasy.organization.repository.OrganizationDocumentRepository;
import com.bookeasy.organization.repository.OrganizationOwnerRepository;
import com.bookeasy.organization.repository.OrganizationVerificationRepository;
import com.bookeasy.organization.repository.WorkingHoursRepository;
import com.bookeasy.user.entity.User;
import com.bookeasy.user.repository.UserRepository;

/**
 * Builds organization views and decides what is still missing before submission. Read-only; call inside a
 * transaction.
 */
@Component
public class OrganizationViews {

    private final CategoryRepository categories;
    private final SubcategoryRepository subcategories;
    private final DocumentRequirementRepository requirements;
    private final BranchRepository branches;
    private final WorkingHoursRepository hours;
    private final OrganizationDocumentRepository documents;
    private final UploadedFileRepository files;
    private final OrganizationVerificationRepository verifications;
    private final OrganizationOwnerRepository owners;
    private final UserRepository users;

    public OrganizationViews(CategoryRepository categories, SubcategoryRepository subcategories,
                             DocumentRequirementRepository requirements, BranchRepository branches,
                             WorkingHoursRepository hours, OrganizationDocumentRepository documents,
                             UploadedFileRepository files, OrganizationVerificationRepository verifications,
                             OrganizationOwnerRepository owners, UserRepository users) {
        this.categories = categories;
        this.subcategories = subcategories;
        this.requirements = requirements;
        this.branches = branches;
        this.hours = hours;
        this.documents = documents;
        this.files = files;
        this.verifications = verifications;
        this.owners = owners;
        this.users = users;
    }

    /** Summaries for a list of organizations, with batched lookups. {@code withOwner} adds the primary owner. */
    public List<OrganizationSummary> summaries(List<Organization> orgs, boolean withOwner) {
        if (orgs.isEmpty()) {
            return List.of();
        }
        List<Long> orgIds = orgs.stream().map(Organization::getId).toList();
        Map<Long, Subcategory> subs = byId(subcategories.findAllById(
                orgs.stream().map(Organization::getSubcategoryId).distinct().toList()), Subcategory::getId);
        Map<Long, Category> cats = byId(categories.findAllById(
                subs.values().stream().map(Subcategory::getCategoryId).distinct().toList()), Category::getId);
        Map<Long, String> cityByOrg = new java.util.HashMap<>();
        for (Branch b : branches.findByOrganizationIdInOrderByIdAsc(orgIds)) {
            cityByOrg.putIfAbsent(b.getOrganizationId(), b.getCity());
        }
        Map<Long, Long> logoIds = orgs.stream().filter(o -> o.getLogoFileId() != null)
                .collect(Collectors.toMap(Organization::getId, Organization::getLogoFileId));
        Map<Long, UploadedFile> logos = byId(files.findAllById(logoIds.values()), UploadedFile::getId);

        Map<Long, User> primaryOwner = Map.of();
        if (withOwner) {
            Map<Long, Long> ownerIdByOrg = owners.findByOrganizationIdIn(orgIds).stream()
                    .filter(o -> o.getRole() == OrganizationOwner.Role.PRIMARY)
                    .collect(Collectors.toMap(OrganizationOwner::getOrganizationId, OrganizationOwner::getUserId,
                            (a, b) -> a));
            Map<Long, User> usersById = byId(users.findAllById(ownerIdByOrg.values()), User::getId);
            primaryOwner = ownerIdByOrg.entrySet().stream().filter(e -> usersById.containsKey(e.getValue()))
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> usersById.get(e.getValue())));
        }

        List<OrganizationSummary> out = new ArrayList<>();
        for (Organization o : orgs) {
            Subcategory sub = subs.get(o.getSubcategoryId());
            Category cat = sub == null ? null : cats.get(sub.getCategoryId());
            UploadedFile logo = o.getLogoFileId() == null ? null : logos.get(o.getLogoFileId());
            User owner = primaryOwner.get(o.getId());
            out.add(new OrganizationSummary(o.getId(), o.getName(), o.getSlug(), o.getStatus(), o.getStatusReason(),
                    cat == null ? null : cat.getId(), cat == null ? null : cat.getName(), o.getSubcategoryId(),
                    sub == null ? null : sub.getName(), logo == null ? null : logo.publicUrl(),
                    cityByOrg.get(o.getId()), owner == null ? null : owner.getFullName(),
                    owner == null ? null : owner.getEmail(), o.getSubmittedAt(), o.getApprovedAt(),
                    o.getCreatedAt(), o.getUpdatedAt()));
        }
        return out;
    }

    public OrganizationDetail detail(Organization org, boolean forAdmin) {
        OrganizationSummary summary = summaries(List.of(org), forAdmin).get(0);
        List<Branch> branchList = branches.findByOrganizationIdOrderByIdAsc(org.getId());
        Map<Long, List<DayHoursView>> hoursByBranch = hoursByBranch(branchList);
        List<BranchView> branchViews = branchList.stream()
                .map(b -> branchView(b, hoursByBranch.getOrDefault(b.getId(), List.of()))).toList();

        List<DocumentSlot> slots = documentSlots(org, summary.categoryId());
        List<OrganizationOwner> ownerRows = owners.findByOrganizationId(org.getId());
        Map<Long, User> ownerUsers = byId(users.findAllById(ownerRows.stream().map(OrganizationOwner::getUserId)
                .toList()), User::getId);

        List<OwnerView> ownerViews = forAdmin ? ownerRows.stream()
                .filter(r -> ownerUsers.containsKey(r.getUserId()))
                .map(r -> {
                    User u = ownerUsers.get(r.getUserId());
                    return new OwnerView(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(),
                            u.getEmailVerifiedAt() != null, u.getPhoneVerifiedAt() != null, r.getRole());
                }).toList() : null;

        Optional<User> primary = ownerRows.stream().filter(r -> r.getRole() == OrganizationOwner.Role.PRIMARY)
                .map(r -> ownerUsers.get(r.getUserId())).filter(java.util.Objects::nonNull).findFirst();

        List<HistoryEntry> history = verifications.findByOrganizationIdOrderByCreatedAtAscIdAsc(org.getId()).stream()
                .map(HistoryEntry::of).toList();

        return new OrganizationDetail(summary, org.getDescription(), org.getContactEmail(), org.getContactPhone(),
                org.getWebsite(), org.getGstin(), org.getConfirmationMode(),
                new Editable(org.getStatus().identityEditable(), org.getStatus().operationsEditable()),
                missingSteps(org, branchViews, slots, primary.orElse(null)), branchViews, slots, history,
                ownerViews);
    }

    /** What stops this organization from being submitted for review. Empty means ready. */
    public List<MissingStep> missingSteps(Organization org) {
        Long categoryId = subcategories.findById(org.getSubcategoryId()).map(Subcategory::getCategoryId).orElse(null);
        List<Branch> branchList = branches.findByOrganizationIdOrderByIdAsc(org.getId());
        Map<Long, List<DayHoursView>> hoursByBranch = hoursByBranch(branchList);
        List<BranchView> branchViews = branchList.stream()
                .map(b -> branchView(b, hoursByBranch.getOrDefault(b.getId(), List.of()))).toList();
        User primary = owners.findByOrganizationId(org.getId()).stream()
                .filter(r -> r.getRole() == OrganizationOwner.Role.PRIMARY).findFirst()
                .flatMap(r -> users.findById(r.getUserId())).orElse(null);
        return missingSteps(org, branchViews, documentSlots(org, categoryId), primary);
    }

    private static List<MissingStep> missingSteps(Organization org, List<BranchView> branchViews,
                                                  List<DocumentSlot> slots, User primaryOwner) {
        List<MissingStep> missing = new ArrayList<>();
        if (org.getContactEmail() == null && org.getContactPhone() == null) {
            missing.add(MissingStep.CONTACT);
        }
        List<BranchView> active = branchViews.stream().filter(BranchView::active).toList();
        if (active.isEmpty()) {
            missing.add(MissingStep.BRANCH);
        } else if (active.stream().anyMatch(b -> b.hours().isEmpty())) {
            missing.add(MissingStep.WORKING_HOURS);
        }
        boolean docsMissing = slots.stream().anyMatch(s -> s.requirement().active() && s.requirement().mandatory()
                && (s.document() == null || s.document().status() == OrganizationDocument.Status.REJECTED));
        if (docsMissing) {
            missing.add(MissingStep.DOCUMENTS);
        }
        if (primaryOwner == null
                || (primaryOwner.getEmailVerifiedAt() == null && primaryOwner.getPhoneVerifiedAt() == null)) {
            missing.add(MissingStep.ACCOUNT_VERIFICATION);
        }
        return missing;
    }

    /**
     * Active requirements of the category, plus inactive ones that still have an upload (so reviewers can see
     * every file the business sent).
     */
    public List<DocumentSlot> documentSlots(Organization org, Long categoryId) {
        if (categoryId == null) {
            return List.of();
        }
        Map<Long, OrganizationDocument> docsByReq = documents.findByOrganizationId(org.getId()).stream()
                .collect(Collectors.toMap(OrganizationDocument::getRequirementId, Function.identity()));
        Map<Long, UploadedFile> fileById = byId(files.findAllById(docsByReq.values().stream()
                .map(OrganizationDocument::getFileId).toList()), UploadedFile::getId);
        List<DocumentSlot> slots = new ArrayList<>();
        for (DocumentRequirement r : requirements.findByCategoryIdOrderBySortOrderAscIdAsc(categoryId)) {
            OrganizationDocument d = docsByReq.get(r.getId());
            if (!r.isActive() && d == null) {
                continue;
            }
            DocumentView view = null;
            if (d != null) {
                UploadedFile f = fileById.get(d.getFileId());
                view = new DocumentView(d.getId(), f == null ? null : f.getOriginalName(),
                        f == null ? null : f.getContentType(), f == null ? 0 : f.getSizeBytes(), d.getStatus(),
                        d.getReviewerNote(), d.getUpdatedAt(), d.getReviewedAt());
            }
            slots.add(new DocumentSlot(RequirementView.of(r), view));
        }
        return slots;
    }

    public static BranchView branchView(Branch b, List<DayHoursView> hours) {
        return new BranchView(b.getId(), b.getName(), b.getAddressLine1(), b.getAddressLine2(), b.getLocality(),
                b.getCity(), b.getState(), b.getPincode(), b.getLatitude(), b.getLongitude(), b.getPhone(),
                b.getTimezone(), b.isActive(), hours);
    }

    public Map<Long, List<DayHoursView>> hoursByBranch(Collection<Branch> branchList) {
        if (branchList.isEmpty()) {
            return Map.of();
        }
        return hours.findByBranchIdInOrderByBranchIdAscDayOfWeekAsc(branchList.stream().map(Branch::getId).toList())
                .stream()
                .sorted(Comparator.comparing(WorkingHours::getBranchId).thenComparing(WorkingHours::getDayOfWeek))
                .collect(Collectors.groupingBy(WorkingHours::getBranchId, Collectors.mapping(
                        w -> new DayHoursView(w.getDayOfWeek(), w.getOpensAt(), w.getClosesAt(), w.getBreakStart(),
                                w.getBreakEnd()), Collectors.toList())));
    }

    private static <T> Map<Long, T> byId(Iterable<T> items, Function<T, Long> id) {
        Map<Long, T> map = new java.util.HashMap<>();
        items.forEach(i -> map.put(id.apply(i), i));
        return map;
    }
}
