package com.bookeasy.organization.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.bookeasy.organization.dto.OrganizationDtos.BranchRequest;
import com.bookeasy.organization.dto.OrganizationDtos.BranchView;
import com.bookeasy.organization.dto.OrganizationDtos.DayHours;
import com.bookeasy.organization.dto.OrganizationDtos.HolidayRequest;
import com.bookeasy.organization.dto.OrganizationDtos.HolidayView;
import com.bookeasy.organization.dto.OrganizationDtos.HoursRequest;
import com.bookeasy.organization.entity.Branch;
import com.bookeasy.organization.entity.Holiday;
import com.bookeasy.organization.entity.Organization;
import com.bookeasy.organization.entity.WorkingHours;
import com.bookeasy.organization.repository.BranchRepository;
import com.bookeasy.organization.repository.HolidayRepository;
import com.bookeasy.organization.repository.WorkingHoursRepository;

/** Branches, weekly hours and holidays of an owner's organization. */
@Service
public class BranchService {

    static final int MAX_BRANCHES = 50;
    static final int MAX_UPCOMING_HOLIDAYS = 120;
    /** Holidays can be planned up to this far ahead. */
    static final int HOLIDAY_HORIZON_YEARS = 2;

    private final BranchRepository branches;
    private final WorkingHoursRepository hours;
    private final HolidayRepository holidays;
    private final OrganizationAccess access;
    private final OrganizationViews views;
    private final Clock clock;

    public BranchService(BranchRepository branches, WorkingHoursRepository hours, HolidayRepository holidays,
                         OrganizationAccess access, OrganizationViews views, Clock clock) {
        this.branches = branches;
        this.hours = hours;
        this.holidays = holidays;
        this.access = access;
        this.views = views;
        this.clock = clock;
    }

    @Transactional
    public BranchView create(long userId, long organizationId, BranchRequest req) {
        Organization org = access.ownedForOperationsEdit(userId, organizationId);
        if (branches.countByOrganizationId(org.getId()) >= MAX_BRANCHES) {
            throw new ApiException(ErrorCode.BRANCH_LIMIT_REACHED);
        }
        Instant now = clock.instant();
        Branch branch = new Branch(org.getId(), now);
        apply(branch, req, now);
        branches.save(branch);
        org.touch(now);
        return view(branch);
    }

    @Transactional
    public BranchView update(long userId, long organizationId, long branchId, BranchRequest req) {
        Organization org = access.ownedForOperationsEdit(userId, organizationId);
        Branch branch = branch(org, branchId);
        Instant now = clock.instant();
        apply(branch, req, now);
        org.touch(now);
        return view(branch);
    }

    /**
     * Branches can be removed only before approval. Afterwards bookings may point at them, so owners deactivate
     * them instead.
     */
    @Transactional
    public void delete(long userId, long organizationId, long branchId) {
        Organization org = access.ownedForIdentityEdit(userId, organizationId);
        branches.delete(branch(org, branchId));
        org.touch(clock.instant());
    }

    /** Replaces the whole weekly schedule. Days not listed are closed. */
    @Transactional
    public BranchView setHours(long userId, long organizationId, long branchId, HoursRequest req) {
        Organization org = access.ownedForOperationsEdit(userId, organizationId);
        Branch branch = branch(org, branchId);
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < req.days().size(); i++) {
            DayHours d = req.days().get(i);
            String field = "days[" + i + "]";
            if (!seen.add(d.dayOfWeek())) {
                throw ApiException.invalidField(field + ".dayOfWeek", "DUPLICATE_DAY", "Each day can appear once");
            }
            if (!d.opensAt().isBefore(d.closesAt())) {
                throw ApiException.invalidField(field + ".closesAt", "CLOSES_BEFORE_OPENS",
                        "Closing time must be after opening time");
            }
            if ((d.breakStart() == null) != (d.breakEnd() == null)) {
                throw ApiException.invalidField(field + ".breakEnd", "BREAK_INCOMPLETE", "Give both break times");
            }
            if (d.breakStart() != null && !(d.opensAt().isBefore(d.breakStart())
                    && d.breakStart().isBefore(d.breakEnd()) && d.breakEnd().isBefore(d.closesAt()))) {
                throw ApiException.invalidField(field + ".breakStart", "BREAK_OUTSIDE_HOURS",
                        "The break must fall inside opening hours");
            }
        }
        hours.deleteAllForBranch(branch.getId());
        hours.saveAll(req.days().stream()
                .map(d -> new WorkingHours(branch.getId(), d.dayOfWeek(), d.opensAt(), d.closesAt(), d.breakStart(),
                        d.breakEnd()))
                .toList());
        hours.flush();
        org.touch(clock.instant());
        return view(branch);
    }

    @Transactional(readOnly = true)
    public List<HolidayView> holidays(long userId, long organizationId, long branchId) {
        Organization org = access.owned(userId, organizationId);
        Branch branch = branch(org, branchId);
        return holidays.findByBranchIdAndDateGreaterThanEqualOrderByDateAsc(branch.getId(), today(branch)).stream()
                .map(h -> new HolidayView(h.getId(), h.getDate(), h.getReason())).toList();
    }

    @Transactional
    public HolidayView addHoliday(long userId, long organizationId, long branchId, HolidayRequest req) {
        Organization org = access.ownedForOperationsEdit(userId, organizationId);
        Branch branch = branch(org, branchId);
        LocalDate today = today(branch);
        if (req.date().isBefore(today)) {
            throw ApiException.invalidField("date", "DATE_IN_PAST", "Choose today or a later date");
        }
        if (req.date().isAfter(today.plusYears(HOLIDAY_HORIZON_YEARS))) {
            throw ApiException.invalidField("date", "DATE_TOO_FAR", "Choose a date within two years");
        }
        if (holidays.countByBranchIdAndDateGreaterThanEqual(branch.getId(), today) >= MAX_UPCOMING_HOLIDAYS) {
            throw ApiException.invalidField("date", "TOO_MANY_HOLIDAYS", "Too many upcoming holidays");
        }
        if (holidays.existsByBranchIdAndDate(branch.getId(), req.date())) {
            throw ApiException.invalidField("date", "DUPLICATE_DATE", "This date is already a holiday");
        }
        try {
            Holiday h = holidays.saveAndFlush(new Holiday(branch.getId(), req.date(), Inputs.text(req.reason()),
                    clock.instant()));
            return new HolidayView(h.getId(), h.getDate(), h.getReason());
        } catch (DataIntegrityViolationException e) {
            throw ApiException.invalidField("date", "DUPLICATE_DATE", "This date is already a holiday");
        }
    }

    @Transactional
    public void deleteHoliday(long userId, long organizationId, long branchId, long holidayId) {
        Organization org = access.ownedForOperationsEdit(userId, organizationId);
        Branch branch = branch(org, branchId);
        holidays.delete(holidays.findByIdAndBranchId(holidayId, branch.getId())
                .orElseThrow(() -> ApiException.notFound("Holiday")));
    }

    private Branch branch(Organization org, long branchId) {
        return branches.findByIdAndOrganizationId(branchId, org.getId())
                .orElseThrow(() -> ApiException.notFound("Branch"));
    }

    private LocalDate today(Branch branch) {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(branch.getTimezone()));
    }

    private BranchView view(Branch branch) {
        return OrganizationViews.branchView(branch,
                views.hoursByBranch(List.of(branch)).getOrDefault(branch.getId(), List.of()));
    }

    private static void apply(Branch branch, BranchRequest req, Instant now) {
        branch.update(Inputs.text(req.name()), Inputs.text(req.addressLine1()), Inputs.text(req.addressLine2()),
                Inputs.text(req.locality()), Inputs.text(req.city()), Inputs.text(req.state()), req.pincode(),
                req.latitude().setScale(6, java.math.RoundingMode.HALF_UP),
                req.longitude().setScale(6, java.math.RoundingMode.HALF_UP),
                Inputs.phone("phone", req.phone()), Inputs.timezone(req.timezone()),
                req.active() == null || req.active(), now);
    }
}
