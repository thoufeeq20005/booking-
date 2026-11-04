package com.bookeasy.availability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.bookeasy.availability.SlotCalculator.BranchDay;
import com.bookeasy.availability.SlotCalculator.Interval;
import com.bookeasy.availability.SlotCalculator.Outcome;
import com.bookeasy.availability.SlotCalculator.Resource;
import com.bookeasy.availability.SlotCalculator.Result;
import com.bookeasy.availability.SlotCalculator.Rules;
import com.bookeasy.availability.SlotCalculator.Slot;
import com.bookeasy.availability.SlotCalculator.Window;

class SlotCalculatorTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 10);
    /** "Now" is the evening before, so minimum notice doesn't interfere unless a test wants it to. */
    private static final Instant NOW = ZonedDateTime.of(DAY.minusDays(1), LocalTime.of(20, 0), IST).toInstant();
    private static final BranchDay NINE_TO_SIX_LUNCH = new BranchDay(t("09:00"), t("18:00"), t("13:00"), t("14:00"));
    private static final Rules THIRTY_MIN = new Rules(30, 0, 30, 60, 30);

    private static LocalTime t(String s) {
        return LocalTime.parse(s);
    }

    private static Instant at(String time) {
        return ZonedDateTime.of(DAY, t(time), IST).toInstant();
    }

    private static Resource staff(long id, Window... windows) {
        return new Resource(id, List.of(windows), List.of(), List.of());
    }

    private static Window w(String a, String b) {
        return new Window(t(a), t(b));
    }

    private static List<String> times(Result r) {
        return r.slots().stream().map(s -> s.time().toString()).toList();
    }

    @Test
    void offersAlignedSlotsInsideStaffAndBranchHoursAndSkipsTheBreak() {
        Result r = SlotCalculator.compute(DAY, IST, NINE_TO_SIX_LUNCH, false, THIRTY_MIN,
                List.of(staff(1, w("08:00", "11:00"), w("12:10", "15:00"))), NOW);
        assertThat(r.outcome()).isEqualTo(Outcome.OPEN);
        // 08:00–09:00 is before opening; 12:10 aligns up to 12:30; 13:00–14:00 is lunch.
        assertThat(times(r)).containsExactly("09:00", "09:30", "10:00", "10:30", "12:30", "14:00", "14:30");
        Slot first = r.slots().get(0);
        assertThat(first.startsAt()).isEqualTo(Instant.parse("2026-11-10T03:30:00Z"));
        assertThat(first.endsAt()).isEqualTo(Instant.parse("2026-11-10T04:00:00Z"));
        assertThat(first.staffIds()).containsExactly(1L);
    }

    @Test
    void appointmentMustEndBeforeClosingButBufferMaySpillOver() {
        Rules fortyFive = new Rules(45, 15, 15, 0, 30);
        Result r = SlotCalculator.compute(DAY, IST, new BranchDay(t("17:00"), t("18:00"), null, null), false, fortyFive,
                List.of(staff(1, w("09:00", "18:00"))), NOW);
        assertThat(times(r)).containsExactly("17:00", "17:15");
    }

    @Test
    void busyTimeIncludingBufferBlocksOverlappingSlots() {
        // Existing booking 10:00–10:30 (its own buffer already included by the provider).
        Resource busy = new Resource(1L, List.of(w("09:00", "12:00")), List.of(),
                List.of(new Interval(at("10:00"), at("10:30"))));
        Rules withBuffer = new Rules(30, 15, 15, 0, 30);
        Result r = SlotCalculator.compute(DAY, IST, NINE_TO_SIX_LUNCH, false, withBuffer, List.of(busy), NOW);
        // 09:30 would run until 10:00 + 15 min buffer = 10:15 → conflicts. 09:15 ends 09:45 + buffer 10:00 → fine.
        assertThat(times(r)).contains("09:00", "09:15").doesNotContain("09:30", "09:45", "10:00", "10:15")
                .contains("10:30", "11:30");
        assertThat(times(r)).doesNotContain("11:45"); // would end after the 12:00 window
    }

    @Test
    void leaveRemovesOverlappingSlotsOnly() {
        Resource onLeave = new Resource(1L, List.of(w("09:00", "12:00")),
                List.of(new Interval(at("10:15"), at("11:00"))), List.of());
        Result r = SlotCalculator.compute(DAY, IST, NINE_TO_SIX_LUNCH, false, THIRTY_MIN, List.of(onLeave), NOW);
        assertThat(times(r)).containsExactly("09:00", "09:30", "11:00", "11:30");
    }

    @Test
    void minimumNoticeHidesSlotsTooSoon() {
        Instant tenAm = at("10:00");
        Result r = SlotCalculator.compute(DAY, IST, NINE_TO_SIX_LUNCH, false, THIRTY_MIN,
                List.of(staff(1, w("09:00", "12:30"))), tenAm);
        assertThat(times(r)).containsExactly("11:00", "11:30", "12:00");
    }

    @Test
    void mergesStaffPerStartTime() {
        Result r = SlotCalculator.compute(DAY, IST, NINE_TO_SIX_LUNCH, false, THIRTY_MIN,
                List.of(staff(7, w("09:00", "10:00")), staff(3, w("09:30", "10:30"))), NOW);
        assertThat(r.slots()).extracting(s -> s.time() + "=" + s.staffIds())
                .containsExactly("09:00=[7]", "09:30=[3, 7]", "10:00=[3]");
    }

    @Test
    void reportsWhyADayHasNoSlots() {
        List<Resource> one = List.of(staff(1, w("09:00", "12:00")));
        assertThat(SlotCalculator.compute(DAY, IST, NINE_TO_SIX_LUNCH, true, THIRTY_MIN, one, NOW).outcome())
                .isEqualTo(Outcome.HOLIDAY);
        assertThat(SlotCalculator.compute(DAY, IST, null, false, THIRTY_MIN, one, NOW).outcome())
                .isEqualTo(Outcome.CLOSED);
        assertThat(SlotCalculator.compute(DAY, IST, NINE_TO_SIX_LUNCH, false, THIRTY_MIN, List.of(), NOW).outcome())
                .isEqualTo(Outcome.NO_STAFF);
        assertThat(SlotCalculator.compute(DAY.minusDays(2), IST, NINE_TO_SIX_LUNCH, false, THIRTY_MIN, one, NOW)
                .outcome()).isEqualTo(Outcome.OUT_OF_RANGE);
        assertThat(SlotCalculator.compute(DAY.plusDays(31), IST, NINE_TO_SIX_LUNCH, false, THIRTY_MIN, one, NOW)
                .outcome()).isEqualTo(Outcome.OUT_OF_RANGE);
    }

    @Test
    void lateNightWindowsNeverWrapPastMidnight() {
        Result r = SlotCalculator.compute(DAY, IST, new BranchDay(t("22:00"), t("23:59"), null, null), false,
                new Rules(45, 0, 15, 0, 30), List.of(staff(1, w("22:00", "23:59"))), NOW);
        assertThat(times(r)).first().isEqualTo("22:00");
        assertThat(times(r)).last().isEqualTo("23:00");
    }

    @Test
    void alignmentAndIntersection() {
        assertThat(SlotCalculator.alignUp(t("09:07"), 15)).isEqualTo(t("09:15"));
        assertThat(SlotCalculator.alignUp(t("09:15"), 15)).isEqualTo(t("09:15"));
        assertThat(SlotCalculator.alignUp(t("23:50"), 15)).isNull();
        assertThat(SlotCalculator.intersect(List.of(w("08:00", "12:00")), List.of(w("09:00", "13:00"), w("14:00", "18:00"))))
                .containsExactly(w("09:00", "12:00"));
    }
}
