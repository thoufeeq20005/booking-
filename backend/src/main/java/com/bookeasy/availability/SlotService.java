package com.bookeasy.availability;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.availability.SlotCalculator.BranchDay;
import com.bookeasy.availability.SlotCalculator.Interval;
import com.bookeasy.availability.SlotCalculator.Resource;
import com.bookeasy.availability.SlotCalculator.Rules;
import com.bookeasy.availability.SlotCalculator.Window;
import com.bookeasy.catalog.BranchService;
import com.bookeasy.catalog.BranchServiceRepository;
import com.bookeasy.catalog.ServiceOffering;
import com.bookeasy.catalog.ServiceRepository;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.organization.entity.Branch;
import com.bookeasy.organization.entity.Organization;
import com.bookeasy.organization.entity.OrganizationStatus;
import com.bookeasy.organization.entity.WorkingHours;
import com.bookeasy.organization.repository.BranchRepository;
import com.bookeasy.organization.repository.HolidayRepository;
import com.bookeasy.organization.repository.OrganizationRepository;
import com.bookeasy.organization.repository.WorkingHoursRepository;
import com.bookeasy.organization.service.OrganizationAccess;
import com.bookeasy.staff.StaffAvailability;
import com.bookeasy.staff.StaffAvailabilityRepository;
import com.bookeasy.staff.StaffLeave;
import com.bookeasy.staff.StaffLeaveRepository;
import com.bookeasy.staff.StaffProfile;
import com.bookeasy.staff.StaffProfileRepository;

/** Loads everything a day's slots depend on and hands it to {@link SlotCalculator}. */
@Service
public class SlotService {

    private final OrganizationRepository organizations;
    private final BranchRepository branches;
    private final ServiceRepository services;
    private final BranchServiceRepository branchServices;
    private final WorkingHoursRepository hours;
    private final HolidayRepository holidays;
    private final StaffProfileRepository staff;
    private final StaffAvailabilityRepository availability;
    private final StaffLeaveRepository leaves;
    private final BusyTimeProvider busyTime;
    private final OrganizationAccess access;
    private final Clock clock;

    public SlotService(OrganizationRepository organizations, BranchRepository branches, ServiceRepository services,
                       BranchServiceRepository branchServices, WorkingHoursRepository hours, HolidayRepository holidays,
                       StaffProfileRepository staff, StaffAvailabilityRepository availability,
                       StaffLeaveRepository leaves, BusyTimeProvider busyTime, OrganizationAccess access, Clock clock) {
        this.organizations = organizations;
        this.branches = branches;
        this.services = services;
        this.branchServices = branchServices;
        this.hours = hours;
        this.holidays = holidays;
        this.staff = staff;
        this.availability = availability;
        this.leaves = leaves;
        this.busyTime = busyTime;
        this.access = access;
        this.clock = clock;
    }

    public record StaffBrief(long id, String displayName, String title) {
    }

    public record SlotView(String time, Instant startsAt, Instant endsAt, List<Long> staffIds) {
    }

    public record DaySlots(LocalDate date, String timezone, long branchId, long serviceId, int durationMinutes,
                           BigDecimal price, SlotCalculator.Outcome outcome, List<StaffBrief> staff,
                           List<SlotView> slots) {
    }

    /** Customer-facing: only approved businesses, active branches and active services. */
    @Transactional(readOnly = true)
    public DaySlots publicSlots(long branchId, long serviceId, LocalDate date, Long staffId) {
        Branch branch = branches.findById(branchId).filter(Branch::isActive)
                .orElseThrow(() -> ApiException.notFound("Branch"));
        Organization org = organizations.findById(branch.getOrganizationId())
                .filter(o -> o.getStatus() == OrganizationStatus.APPROVED)
                .orElseThrow(() -> ApiException.notFound("Branch"));
        ServiceOffering service = services.findByIdAndOrganizationId(serviceId, org.getId())
                .filter(ServiceOffering::isActive).orElseThrow(() -> ApiException.notFound("Service"));
        return compute(branch, service, date, staffId);
    }

    /** Owner preview of their own configuration, in any status. */
    @Transactional(readOnly = true)
    public DaySlots ownerPreview(long userId, long organizationId, long branchId, long serviceId, LocalDate date,
                                 Long staffId) {
        Organization org = access.owned(userId, organizationId);
        Branch branch = branches.findByIdAndOrganizationId(branchId, org.getId())
                .orElseThrow(() -> ApiException.notFound("Branch"));
        ServiceOffering service = services.findByIdAndOrganizationId(serviceId, org.getId())
                .orElseThrow(() -> ApiException.notFound("Service"));
        return compute(branch, service, date, staffId);
    }

    private DaySlots compute(Branch branch, ServiceOffering service, LocalDate date, Long staffFilter) {
        BranchService offer = branchServices.findByBranchIdAndServiceId(branch.getId(), service.getId())
                .orElseThrow(() -> ApiException.notFound("Service"));
        BigDecimal price = offer.getPriceOverride() != null ? offer.getPriceOverride() : service.getPrice();
        ZoneId zone = ZoneId.of(branch.getTimezone());
        int dow = date.getDayOfWeek().getValue();
        WorkingHours wh = hours.findByBranchIdInOrderByBranchIdAscDayOfWeekAsc(List.of(branch.getId())).stream()
                .filter(h -> h.getDayOfWeek() == dow).findFirst().orElse(null);
        BranchDay day = wh == null ? null : new BranchDay(wh.getOpensAt(), wh.getClosesAt(), wh.getBreakStart(),
                wh.getBreakEnd());
        boolean holiday = holidays.existsByBranchIdAndDate(branch.getId(), date);
        Instant dayStart = date.atStartOfDay(zone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();

        List<StaffProfile> candidates = List.of();
        List<Resource> resources = new ArrayList<>();
        if (service.isRequiresStaff()) {
            candidates = staff.findByOrganizationIdAndStatusIn(branch.getOrganizationId(),
                            Set.of(StaffProfile.Status.ACTIVE)).stream()
                    .filter(s -> s.getBranchIds().contains(branch.getId()) && s.getServiceIds().contains(service.getId()))
                    .filter(s -> staffFilter == null || s.getId().equals(staffFilter))
                    .sorted(Comparator.comparing(StaffProfile::getDisplayName)).toList();
            List<Long> ids = candidates.stream().map(StaffProfile::getId).toList();
            if (!ids.isEmpty()) {
                Map<Long, List<Window>> windows = availability.findByBranchIdAndDayOfWeekAndStaffIdIn(branch.getId(), dow, ids)
                        .stream().collect(Collectors.groupingBy(StaffAvailability::getStaffId, Collectors.mapping(
                                a -> new Window(a.getStartTime(), a.getEndTime()), Collectors.toList())));
                Map<Long, List<Interval>> leave = leaves.overlapping(ids, dayStart, dayEnd).stream()
                        .collect(Collectors.groupingBy(StaffLeave::getStaffId, Collectors.mapping(
                                l -> new Interval(l.getStartsAt(), l.getEndsAt()), Collectors.toList())));
                Map<Long, List<Interval>> busy = busyTime.busy(branch.getId(), service.getId(), ids, dayStart, dayEnd)
                        .stream().filter(b -> b.staffId() != null)
                        .collect(Collectors.groupingBy(BusyTimeProvider.Busy::staffId, Collectors.mapping(
                                b -> new Interval(b.start(), b.end()), Collectors.toList())));
                for (Long id : ids) {
                    List<Window> w = windows.getOrDefault(id, List.of());
                    if (!w.isEmpty()) {
                        resources.add(new Resource(id, w, leave.getOrDefault(id, List.of()), busy.getOrDefault(id, List.of())));
                    }
                }
            }
        } else {
            List<Interval> busy = busyTime.busy(branch.getId(), service.getId(), List.of(), dayStart, dayEnd).stream()
                    .filter(b -> b.staffId() == null).map(b -> new Interval(b.start(), b.end())).toList();
            resources.add(new Resource(null, List.of(new Window(LocalTime.MIN, LocalTime.MAX)), List.of(), busy));
        }

        Rules rules = new Rules(service.getDurationMinutes(), service.getBufferMinutes(),
                service.getSlotIntervalMinutes(), service.getMinNoticeMinutes(), service.getMaxAdvanceDays());
        SlotCalculator.Result result = SlotCalculator.compute(date, zone, day, holiday, rules, resources, clock.instant());
        List<SlotView> slots = result.slots().stream()
                .map(s -> new SlotView(s.time().toString(), s.startsAt(), s.endsAt(), s.staffIds())).toList();
        List<StaffBrief> briefs = candidates.stream()
                .map(s -> new StaffBrief(s.getId(), s.getDisplayName(), s.getTitle())).toList();
        return new DaySlots(date, zone.getId(), branch.getId(), service.getId(), service.getDurationMinutes(), price,
                result.outcome(), briefs, slots);
    }
}
