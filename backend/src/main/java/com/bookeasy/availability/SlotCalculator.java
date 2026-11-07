package com.bookeasy.availability;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pure slot arithmetic, no database. A slot [start, start + duration) is offered when it:
 * <ul>
 *   <li>lies inside the branch's opening hours (excluding its break) on a non-holiday;</li>
 *   <li>lies inside one of the resource's working windows (staff availability, or the branch itself);</li>
 *   <li>starts at a multiple of the slot interval counted from midnight, and no earlier than now + minimum notice;</li>
 *   <li>is on a date between today and today + maximum advance days (branch timezone);</li>
 *   <li>does not overlap the resource's leave, and [start, end + buffer) does not overlap its busy time.</li>
 * </ul>
 * The buffer may run past closing time; the appointment itself may not.
 */
public final class SlotCalculator {

    private SlotCalculator() {
    }

    public record Window(LocalTime start, LocalTime end) {
        public Window {
            if (!start.isBefore(end)) {
                throw new IllegalArgumentException("Empty window " + start + "–" + end);
            }
        }
    }

    public record Interval(Instant start, Instant end) {
        boolean overlaps(Instant s, Instant e) {
            return start.isBefore(e) && end.isAfter(s);
        }
    }

    /** Opening hours for the date, or null when the branch is closed that weekday. */
    public record BranchDay(LocalTime opens, LocalTime closes, LocalTime breakStart, LocalTime breakEnd) {
        List<Window> windows() {
            if (breakStart == null) {
                return List.of(new Window(opens, closes));
            }
            return List.of(new Window(opens, breakStart), new Window(breakEnd, closes));
        }
    }

    public record Rules(int durationMinutes, int bufferMinutes, int intervalMinutes, int minNoticeMinutes,
                        int maxAdvanceDays) {
    }

    /** Something that can be booked: a staff member (id) or, for services without staff, the branch (id null). */
    public record Resource(Long staffId, List<Window> windows, List<Interval> leave, List<Interval> busy) {
    }

    public record Slot(LocalTime time, Instant startsAt, Instant endsAt, List<Long> staffIds) {
    }

    public enum Outcome { OPEN, HOLIDAY, CLOSED, OUT_OF_RANGE, NO_STAFF }

    public record Result(Outcome outcome, List<Slot> slots) {
    }

    public static Result compute(LocalDate date, ZoneId zone, BranchDay branchDay, boolean holiday, Rules rules,
                                 List<Resource> resources, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, zone);
        if (date.isBefore(today) || date.isAfter(today.plusDays(rules.maxAdvanceDays()))) {
            return new Result(Outcome.OUT_OF_RANGE, List.of());
        }
        if (holiday) {
            return new Result(Outcome.HOLIDAY, List.of());
        }
        if (branchDay == null) {
            return new Result(Outcome.CLOSED, List.of());
        }
        if (resources.isEmpty()) {
            return new Result(Outcome.NO_STAFF, List.of());
        }

        Instant earliest = now.plus(Duration.ofMinutes(rules.minNoticeMinutes()));
        Duration length = Duration.ofMinutes(rules.durationMinutes());
        Duration buffer = Duration.ofMinutes(rules.bufferMinutes());
        Map<LocalTime, Slot> byTime = new TreeMap<>();

        for (Resource r : resources) {
            for (Window w : intersect(r.windows(), branchDay.windows())) {
                for (LocalTime t = alignUp(w.start(), rules.intervalMinutes());
                     t != null && !t.plus(length).isAfter(w.end()) && t.plus(length).isAfter(t);
                     t = next(t, rules.intervalMinutes())) {
                    Instant start = ZonedDateTime.of(date, t, zone).toInstant();
                    Instant end = start.plus(length);
                    if (start.isBefore(earliest)) {
                        continue;
                    }
                    if (r.leave().stream().anyMatch(l -> l.overlaps(start, end))) {
                        continue;
                    }
                    Instant blockedUntil = end.plus(buffer);
                    if (r.busy().stream().anyMatch(b -> b.overlaps(start, blockedUntil))) {
                        continue;
                    }
                    Slot existing = byTime.get(t);
                    List<Long> staff = new ArrayList<>(existing == null ? List.of() : existing.staffIds());
                    if (r.staffId() != null && !staff.contains(r.staffId())) {
                        staff.add(r.staffId());
                        staff.sort(null);
                    }
                    byTime.put(t, new Slot(t, start, end, List.copyOf(staff)));
                }
            }
        }
        return new Result(Outcome.OPEN, List.copyOf(byTime.values()));
    }

    /** Pairwise intersection of two sets of same-day windows. */
    static List<Window> intersect(List<Window> a, List<Window> b) {
        List<Window> out = new ArrayList<>();
        for (Window x : a) {
            for (Window y : b) {
                LocalTime s = x.start().isAfter(y.start()) ? x.start() : y.start();
                LocalTime e = x.end().isBefore(y.end()) ? x.end() : y.end();
                if (s.isBefore(e)) {
                    out.add(new Window(s, e));
                }
            }
        }
        return out;
    }

    /** First clock-aligned start at or after {@code t}, or null if none remains today. */
    static LocalTime alignUp(LocalTime t, int intervalMinutes) {
        int minutes = t.getHour() * 60 + t.getMinute() + (t.getSecond() > 0 || t.getNano() > 0 ? 1 : 0);
        int aligned = ((minutes + intervalMinutes - 1) / intervalMinutes) * intervalMinutes;
        return aligned >= 24 * 60 ? null : LocalTime.of(aligned / 60, aligned % 60);
    }

    private static LocalTime next(LocalTime t, int intervalMinutes) {
        LocalTime n = t.plusMinutes(intervalMinutes);
        return n.isAfter(t) ? n : null; // wrapped past midnight
    }
}
