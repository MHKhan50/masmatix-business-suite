package com.masmatix.suite.salon;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Pure booking rules for the salon diary. No Spring, so it is easy to test. */
public final class SlotPlanner {

    public static final LocalTime OPENS = LocalTime.of(9, 0);
    public static final LocalTime CLOSES = LocalTime.of(18, 0);
    public static final int STEP_MINUTES = 15;

    public record Interval(LocalDateTime start, LocalDateTime end) {}

    private SlotPlanner() {}

    /** Half-open intervals: a booking ending at 10:45 does not clash with one starting at 10:45. */
    public static boolean overlaps(LocalDateTime aStart, LocalDateTime aEnd, LocalDateTime bStart, LocalDateTime bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    public static boolean withinOpeningHours(LocalDateTime start, LocalDateTime end) {
        return start.toLocalDate().equals(end.toLocalDate())
                && !start.toLocalTime().isBefore(OPENS)
                && !end.toLocalTime().isAfter(CLOSES);
    }

    public static boolean onSlot(LocalDateTime start) {
        return start.getMinute() % STEP_MINUTES == 0 && start.getSecond() == 0 && start.getNano() == 0;
    }

    /** Every start time on the day where a treatment of this length fits without touching a busy interval. */
    public static List<LocalTime> freeStarts(LocalDate day, int durationMinutes, List<Interval> busy, LocalDateTime notBefore) {
        List<LocalTime> free = new ArrayList<>();
        LocalDateTime start = day.atTime(OPENS);
        LocalDateTime lastEnd = day.atTime(CLOSES);
        while (!start.plusMinutes(durationMinutes).isAfter(lastEnd)) {
            LocalDateTime end = start.plusMinutes(durationMinutes);
            boolean clash = false;
            for (Interval b : busy) {
                if (overlaps(start, end, b.start(), b.end())) {
                    clash = true;
                    break;
                }
            }
            if (!clash && !start.isBefore(notBefore)) {
                free.add(start.toLocalTime());
            }
            start = start.plusMinutes(STEP_MINUTES);
        }
        return free;
    }
}
