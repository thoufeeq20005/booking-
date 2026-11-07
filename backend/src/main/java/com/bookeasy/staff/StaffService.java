package com.bookeasy.staff;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.audit.AuditService;
import com.bookeasy.auth.support.Identifiers;
import com.bookeasy.catalog.ServiceOffering;
import com.bookeasy.catalog.ServiceRepository;
import com.bookeasy.common.config.BookEasyProperties;
import com.bookeasy.common.crypto.CryptoService;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.bookeasy.common.ratelimit.RateLimiter;
import com.bookeasy.notification.channel.EmailSender;
import com.bookeasy.notification.channel.EmailSender.EmailMessage;
import com.bookeasy.organization.entity.Branch;
import com.bookeasy.organization.entity.Organization;
import com.bookeasy.organization.repository.BranchRepository;
import com.bookeasy.organization.repository.OrganizationRepository;
import com.bookeasy.organization.service.OrganizationAccess;
import com.bookeasy.staff.StaffDtos.AvailabilityRequest;
import com.bookeasy.staff.StaffDtos.InvitationPreview;
import com.bookeasy.staff.StaffDtos.InvitationView;
import com.bookeasy.staff.StaffDtos.InviteRequest;
import com.bookeasy.staff.StaffDtos.LeaveRequest;
import com.bookeasy.staff.StaffDtos.LeaveView;
import com.bookeasy.staff.StaffDtos.Named;
import com.bookeasy.staff.StaffDtos.StaffRequest;
import com.bookeasy.staff.StaffDtos.StaffView;
import com.bookeasy.staff.StaffDtos.WindowDto;
import com.bookeasy.staff.StaffDtos.Workplace;
import com.bookeasy.user.entity.RoleCode;
import com.bookeasy.user.entity.User;
import com.bookeasy.user.repository.UserRepository;

/**
 * Staff of an organization: email invitations, the owner adding themselves, assignments, permissions,
 * weekly availability and leave. Owners act through {@link OrganizationAccess} (tenant isolation);
 * staff with CAN_EDIT_HOURS may edit their own availability and leave.
 */
@Service
public class StaffService {

    private static final Logger log = LoggerFactory.getLogger(StaffService.class);
    static final Duration INVITATION_TTL = Duration.ofDays(7);
    static final int MAX_STAFF = 200;

    private final StaffProfileRepository profiles;
    private final StaffAvailabilityRepository availability;
    private final StaffLeaveRepository leaves;
    private final StaffInvitationRepository invitations;
    private final BranchRepository branches;
    private final ServiceRepository services;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final OrganizationAccess access;
    private final AuditService audit;
    private final CryptoService crypto;
    private final EmailSender emailSender;
    private final MessageSource messages;
    private final BookEasyProperties properties;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public StaffService(StaffProfileRepository profiles, StaffAvailabilityRepository availability,
                        StaffLeaveRepository leaves, StaffInvitationRepository invitations, BranchRepository branches,
                        ServiceRepository services, OrganizationRepository organizations, UserRepository users,
                        OrganizationAccess access, AuditService audit, CryptoService crypto, EmailSender emailSender,
                        MessageSource messages, BookEasyProperties properties, RateLimiter rateLimiter, Clock clock) {
        this.profiles = profiles;
        this.availability = availability;
        this.leaves = leaves;
        this.invitations = invitations;
        this.branches = branches;
        this.services = services;
        this.organizations = organizations;
        this.users = users;
        this.access = access;
        this.audit = audit;
        this.crypto = crypto;
        this.emailSender = emailSender;
        this.messages = messages;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    // =====================================================================================================
    // Owner
    // =====================================================================================================

    @Transactional(readOnly = true)
    public List<StaffView> list(long userId, long organizationId) {
        Organization org = access.owned(userId, organizationId);
        return views(profiles.findByOrganizationIdOrderByDisplayNameAsc(org.getId()));
    }

    @Transactional
    public StaffView invite(long ownerId, long organizationId, InviteRequest req, String ip) {
        Organization org = access.ownedForOperationsEdit(ownerId, organizationId);
        rateLimiter.hit("invite:org:" + org.getId(), 50, Duration.ofDays(1));
        String email = Identifiers.normalizeEmail(req.email());
        requireRoom(org);
        for (StaffProfile p : profiles.findByOrganizationIdOrderByDisplayNameAsc(org.getId())) {
            if (email.equals(emailOf(p))) {
                throw new ApiException(ErrorCode.STAFF_ALREADY_EXISTS);
            }
        }
        Instant now = clock.instant();
        StaffProfile p = new StaffProfile(org.getId(), now);
        applyProfile(org, p, req.profile(), now);
        profiles.saveAndFlush(p);
        sendInvitation(org, p, email, ownerId, now);
        audit.record(ownerId, RoleCode.BUSINESS_OWNER.name(), "STAFF_INVITED", "ORGANIZATION",
                String.valueOf(org.getId()), Map.of("staffId", p.getId(), "email", email), ip);
        return views(List.of(p)).get(0);
    }

    /** Solo practitioners: the owner becomes an active staff member of their own business without an invitation. */
    @Transactional
    public StaffView addSelf(long ownerId, long organizationId, StaffRequest req, String ip) {
        Organization org = access.ownedForOperationsEdit(ownerId, organizationId);
        if (profiles.findByOrganizationIdAndUserId(org.getId(), ownerId).isPresent()) {
            throw new ApiException(ErrorCode.STAFF_ALREADY_EXISTS);
        }
        requireRoom(org);
        Instant now = clock.instant();
        StaffProfile p = new StaffProfile(org.getId(), now);
        applyProfile(org, p, req, now);
        p.link(ownerId, now);
        profiles.saveAndFlush(p);
        grantStaffRole(ownerId, now);
        audit.record(ownerId, RoleCode.BUSINESS_OWNER.name(), "STAFF_JOINED", "ORGANIZATION",
                String.valueOf(org.getId()), Map.of("staffId", p.getId(), "self", true), ip);
        return views(List.of(p)).get(0);
    }

    @Transactional
    public StaffView update(long ownerId, long organizationId, long staffId, StaffRequest req, String ip) {
        Organization org = access.ownedForOperationsEdit(ownerId, organizationId);
        StaffProfile p = profile(org, staffId);
        Set<StaffProfile.Permission> before = Set.copyOf(p.getPermissions());
        applyProfile(org, p, req, clock.instant());
        // Windows at branches the person no longer works at are dropped.
        List<StaffAvailability> kept = availability.findByStaffIdInOrderByDayOfWeekAscStartTimeAsc(List.of(p.getId()))
                .stream().filter(a -> p.getBranchIds().contains(a.getBranchId())).toList();
        replaceAvailability(p, kept);
        if (!before.equals(p.getPermissions())) {
            audit.record(ownerId, RoleCode.BUSINESS_OWNER.name(), "STAFF_PERMISSIONS_CHANGED", "ORGANIZATION",
                    String.valueOf(org.getId()), Map.of("staffId", p.getId(), "permissions", p.getPermissions()), ip);
        }
        return views(List.of(p)).get(0);
    }

    @Transactional
    public StaffView setStatus(long ownerId, long organizationId, long staffId, StaffProfile.Status status, String ip) {
        Organization org = access.ownedForOperationsEdit(ownerId, organizationId);
        StaffProfile p = profile(org, staffId);
        if (status == StaffProfile.Status.INVITED || p.getStatus() == StaffProfile.Status.INVITED) {
            throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        if (p.getStatus() != status) {
            p.setStatus(status, clock.instant());
            audit.record(ownerId, RoleCode.BUSINESS_OWNER.name(), "STAFF_STATUS_CHANGED", "ORGANIZATION",
                    String.valueOf(org.getId()), Map.of("staffId", p.getId(), "to", status.name()), ip);
        }
        return views(List.of(p)).get(0);
    }

    @Transactional
    public StaffView resendInvitation(long ownerId, long organizationId, long staffId) {
        Organization org = access.ownedForOperationsEdit(ownerId, organizationId);
        StaffProfile p = profile(org, staffId);
        if (p.getStatus() != StaffProfile.Status.INVITED) {
            throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        Instant now = clock.instant();
        String email = latestInvitation(p).map(StaffInvitation::getEmail).orElseThrow();
        invitations.findByStaffProfileIdOrderByCreatedAtDesc(p.getId()).stream()
                .filter(i -> i.isOpenAt(now)).forEach(i -> i.revoke(now));
        sendInvitation(org, p, email, ownerId, now);
        return views(List.of(p)).get(0);
    }

    /** Withdraws an open invitation; the placeholder profile goes with it. */
    @Transactional
    public void revokeInvitation(long ownerId, long organizationId, long staffId, String ip) {
        Organization org = access.ownedForOperationsEdit(ownerId, organizationId);
        StaffProfile p = profile(org, staffId);
        if (p.getStatus() != StaffProfile.Status.INVITED) {
            throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        profiles.delete(p);
        audit.record(ownerId, RoleCode.BUSINESS_OWNER.name(), "STAFF_INVITATION_REVOKED", "ORGANIZATION",
                String.valueOf(org.getId()), Map.of("staffId", staffId), ip);
    }

    @Transactional
    public StaffView setAvailability(long ownerId, long organizationId, long staffId, AvailabilityRequest req) {
        Organization org = access.ownedForOperationsEdit(ownerId, organizationId);
        return saveAvailability(profile(org, staffId), req);
    }

    @Transactional
    public StaffView addLeave(long actorId, long organizationId, long staffId, LeaveRequest req) {
        Organization org = access.ownedForOperationsEdit(actorId, organizationId);
        return saveLeave(profile(org, staffId), req, actorId);
    }

    @Transactional
    public StaffView deleteLeave(long ownerId, long organizationId, long staffId, long leaveId) {
        Organization org = access.ownedForOperationsEdit(ownerId, organizationId);
        StaffProfile p = profile(org, staffId);
        leaves.delete(leaves.findByIdAndStaffId(leaveId, p.getId()).orElseThrow(() -> ApiException.notFound("Leave")));
        leaves.flush();
        return views(List.of(p)).get(0);
    }

    // =====================================================================================================
    // Invitations (invitee side)
    // =====================================================================================================

    @Transactional(readOnly = true)
    public InvitationPreview preview(String token) {
        StaffInvitation inv = openInvitation(token);
        StaffProfile p = profiles.findById(inv.getStaffProfileId()).orElseThrow(() -> new ApiException(ErrorCode.INVALID_TOKEN));
        String orgName = organizations.findById(p.getOrganizationId()).map(Organization::getName).orElse("");
        return new InvitationPreview(orgName, p.getDisplayName(), inv.getEmail(), inv.getExpiresAt());
    }

    /**
     * Joins the business. The signed-in account must use the invited email address; because the link was
     * delivered to that address, accepting also verifies it.
     */
    @Transactional
    public Workplace accept(long userId, String token, String ip) {
        StaffInvitation inv = openInvitation(token);
        User user = users.findWithRolesById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        if (user.getEmail() == null || !user.getEmail().equalsIgnoreCase(inv.getEmail())) {
            throw new ApiException(ErrorCode.INVITATION_EMAIL_MISMATCH);
        }
        StaffProfile p = profiles.findById(inv.getStaffProfileId()).orElseThrow(() -> new ApiException(ErrorCode.INVALID_TOKEN));
        if (profiles.findByOrganizationIdAndUserId(p.getOrganizationId(), userId).isPresent()) {
            throw new ApiException(ErrorCode.STAFF_ALREADY_EXISTS);
        }
        Instant now = clock.instant();
        inv.accept(now);
        p.link(userId, now);
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(now);
        }
        grantStaffRole(userId, now);
        audit.record(userId, RoleCode.STAFF.name(), "STAFF_JOINED", "ORGANIZATION",
                String.valueOf(p.getOrganizationId()), Map.of("staffId", p.getId()), ip);
        return workplaces(List.of(p)).get(0);
    }

    // =====================================================================================================
    // Staff member's own view
    // =====================================================================================================

    @Transactional(readOnly = true)
    public List<Workplace> myWorkplaces(long userId) {
        return workplaces(profiles.findByUserIdAndStatus(userId, StaffProfile.Status.ACTIVE));
    }

    @Transactional
    public StaffView setOwnAvailability(long userId, long staffId, AvailabilityRequest req) {
        return saveAvailability(ownEditable(userId, staffId), req);
    }

    @Transactional
    public StaffView addOwnLeave(long userId, long staffId, LeaveRequest req) {
        return saveLeave(ownEditable(userId, staffId), req, userId);
    }

    private StaffProfile ownEditable(long userId, long staffId) {
        StaffProfile p = profiles.findById(staffId)
                .filter(x -> Objects.equals(x.getUserId(), userId) && x.getStatus() == StaffProfile.Status.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("Staff profile"));
        if (!p.can(StaffProfile.Permission.CAN_EDIT_HOURS)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        organizations.findByIdForUpdate(p.getOrganizationId())
                .filter(o -> o.getStatus().operationsEditable())
                .orElseThrow(() -> new ApiException(ErrorCode.ORGANIZATION_NOT_EDITABLE));
        return p;
    }

    // =====================================================================================================
    // Shared
    // =====================================================================================================

    private StaffView saveAvailability(StaffProfile p, AvailabilityRequest req) {
        List<WindowDto> windows = req.windows();
        for (int i = 0; i < windows.size(); i++) {
            WindowDto w = windows.get(i);
            if (!p.getBranchIds().contains(w.branchId())) {
                throw ApiException.invalidField("windows[" + i + "].branchId", "INVALID_BRANCH",
                        "Assign the branch to this person first");
            }
            if (!w.startTime().isBefore(w.endTime())) {
                throw ApiException.invalidField("windows[" + i + "].endTime", "CLOSES_BEFORE_OPENS", "End must be after start");
            }
            for (int j = 0; j < i; j++) {
                WindowDto o = windows.get(j);
                if (o.dayOfWeek() == w.dayOfWeek() && o.startTime().isBefore(w.endTime())
                        && w.startTime().isBefore(o.endTime())) {
                    throw ApiException.invalidField("windows[" + i + "].startTime", "OVERLAPPING_WINDOWS",
                            "Windows on the same day can't overlap");
                }
            }
        }
        replaceAvailability(p, windows.stream()
                .map(w -> new StaffAvailability(p.getId(), w.branchId(), w.dayOfWeek(), w.startTime(), w.endTime()))
                .toList());
        return views(List.of(p)).get(0);
    }

    private void replaceAvailability(StaffProfile p, List<StaffAvailability> rows) {
        List<StaffAvailability> copy = rows.stream()
                .map(a -> new StaffAvailability(p.getId(), a.getBranchId(), a.getDayOfWeek(), a.getStartTime(),
                        a.getEndTime())).toList();
        availability.deleteAllForStaff(p.getId());
        availability.saveAll(copy);
        availability.flush();
    }

    private StaffView saveLeave(StaffProfile p, LeaveRequest req, long actorId) {
        Instant now = clock.instant();
        if (!req.startsAt().isBefore(req.endsAt())) {
            throw ApiException.invalidField("endsAt", "CLOSES_BEFORE_OPENS", "End must be after start");
        }
        if (!req.endsAt().isAfter(now)) {
            throw ApiException.invalidField("endsAt", "DATE_IN_PAST", "Leave must end in the future");
        }
        if (Duration.between(req.startsAt(), req.endsAt()).toDays() > 366) {
            throw ApiException.invalidField("endsAt", "DATE_TOO_FAR", "Leave can be at most one year");
        }
        String reason = req.reason() == null || req.reason().isBlank() ? null : req.reason().strip();
        leaves.saveAndFlush(new StaffLeave(p.getId(), req.startsAt(), req.endsAt(), reason, actorId, now));
        return views(List.of(p)).get(0);
    }

    private void applyProfile(Organization org, StaffProfile p, StaffRequest req, Instant now) {
        Set<Long> orgBranches = branches.findByOrganizationIdOrderByIdAsc(org.getId()).stream().map(Branch::getId)
                .collect(Collectors.toSet());
        Set<Long> orgServices = services.findByOrganizationIdOrderByNameAsc(org.getId()).stream()
                .map(ServiceOffering::getId).collect(Collectors.toSet());
        if (!orgBranches.containsAll(req.branchIds())) {
            throw ApiException.invalidField("branchIds", "INVALID_BRANCH", "Choose this business's branches");
        }
        if (!orgServices.containsAll(req.serviceIds())) {
            throw ApiException.invalidField("serviceIds", "INVALID_SERVICE", "Choose this business's services");
        }
        Set<StaffProfile.Permission> perms = req.permissions() == null || req.permissions().isEmpty()
                ? EnumSet.noneOf(StaffProfile.Permission.class) : EnumSet.copyOf(req.permissions());
        p.update(req.displayName().strip(), blank(req.title()), blank(req.specialisation()), perms, req.branchIds(),
                req.serviceIds(), now);
    }

    private void sendInvitation(Organization org, StaffProfile p, String email, long ownerId, Instant now) {
        String token = crypto.randomToken(32);
        invitations.save(new StaffInvitation(p.getId(), email, crypto.sha256Hex(token), now.plus(INVITATION_TTL),
                ownerId, now));
        String link = properties.frontendUrl() + "/staff/invitations/accept?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
        String lang = users.findById(ownerId).map(User::getPreferredLanguage).orElse("en");
        Locale locale = Locale.forLanguageTag(lang);
        try {
            emailSender.send(new EmailMessage(email,
                    messages.getMessage("email.staffInvite.subject", new Object[]{org.getName()}, locale),
                    messages.getMessage("email.staffInvite.body",
                            new Object[]{p.getDisplayName(), org.getName(), link, INVITATION_TTL.toDays()}, locale)));
        } catch (RuntimeException e) {
            // The owner can resend; the invitation record stays valid.
            log.warn("Could not email staff invitation {} for organization {}", p.getId(), org.getId(), e);
        }
    }

    private StaffInvitation openInvitation(String token) {
        StaffInvitation inv = invitations.findByTokenHash(crypto.sha256Hex(token))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TOKEN));
        Instant now = clock.instant();
        if (inv.getAcceptedAt() != null || inv.getRevokedAt() != null) {
            throw new ApiException(ErrorCode.INVALID_TOKEN);
        }
        if (!inv.getExpiresAt().isAfter(now)) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED);
        }
        return inv;
    }

    private void grantStaffRole(long userId, Instant now) {
        User user = users.findWithRolesById(userId).orElseThrow();
        if (!user.hasRole(RoleCode.STAFF)) {
            user.addRole(RoleCode.STAFF);
            user.touch(now);
        }
    }

    private void requireRoom(Organization org) {
        if (profiles.findByOrganizationIdOrderByDisplayNameAsc(org.getId()).size() >= MAX_STAFF) {
            throw ApiException.invalidField("email", "TOO_MANY", "This business has the maximum number of staff");
        }
    }

    private StaffProfile profile(Organization org, long staffId) {
        return profiles.findByIdAndOrganizationId(staffId, org.getId())
                .orElseThrow(() -> ApiException.notFound("Staff member"));
    }

    private java.util.Optional<StaffInvitation> latestInvitation(StaffProfile p) {
        return invitations.findByStaffProfileIdOrderByCreatedAtDesc(p.getId()).stream().findFirst();
    }

    private String emailOf(StaffProfile p) {
        if (p.getUserId() != null) {
            return users.findById(p.getUserId()).map(User::getEmail).orElse(null);
        }
        return latestInvitation(p).map(StaffInvitation::getEmail).orElse(null);
    }

    private List<StaffView> views(List<StaffProfile> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> ids = list.stream().map(StaffProfile::getId).toList();
        Map<Long, List<WindowDto>> windows = new HashMap<>();
        for (StaffAvailability a : availability.findByStaffIdInOrderByDayOfWeekAscStartTimeAsc(ids)) {
            windows.computeIfAbsent(a.getStaffId(), k -> new ArrayList<>())
                    .add(new WindowDto(a.getBranchId(), a.getDayOfWeek(), a.getStartTime(), a.getEndTime()));
        }
        Instant now = clock.instant();
        List<StaffView> out = new ArrayList<>();
        for (StaffProfile p : list) {
            StaffInvitation inv = p.getStatus() == StaffProfile.Status.INVITED ? latestInvitation(p).orElse(null) : null;
            List<LeaveView> upcoming = leaves.findByStaffIdAndEndsAtAfterOrderByStartsAtAsc(p.getId(), now).stream()
                    .map(l -> new LeaveView(l.getId(), l.getStartsAt(), l.getEndsAt(), l.getReason())).toList();
            out.add(new StaffView(p.getId(), p.getUserId(), p.getDisplayName(), p.getTitle(), p.getSpecialisation(),
                    Set.copyOf(p.getPermissions()), p.getStatus(), Set.copyOf(p.getBranchIds()),
                    Set.copyOf(p.getServiceIds()), emailOf(p),
                    inv == null ? null : new InvitationView(inv.getEmail(), inv.getExpiresAt(), inv.isOpenAt(now)),
                    windows.getOrDefault(p.getId(), List.of()), upcoming));
        }
        return out;
    }

    private List<Workplace> workplaces(List<StaffProfile> list) {
        List<StaffView> views = views(list);
        List<Workplace> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            StaffProfile p = list.get(i);
            Organization org = organizations.findById(p.getOrganizationId()).orElseThrow();
            List<Named> b = branches.findAllById(p.getBranchIds()).stream()
                    .sorted(Comparator.comparing(Branch::getId)).map(x -> new Named(x.getId(), x.getName())).toList();
            List<Named> s = services.findAllById(p.getServiceIds()).stream()
                    .sorted(Comparator.comparing(ServiceOffering::getName)).map(x -> new Named(x.getId(), x.getName()))
                    .toList();
            out.add(new Workplace(org.getId(), org.getName(), views.get(i), b, s));
        }
        return out;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
