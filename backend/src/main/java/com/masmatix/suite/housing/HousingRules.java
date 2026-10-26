package com.masmatix.suite.housing;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Overdue instalments and when a plot may change hands. */
public final class HousingRules {

    private HousingRules() {}

    /** Due yesterday and unpaid: 1 day late. Due today: not late yet. */
    public static long daysLate(LocalDate dueOn, LocalDate paidOn, LocalDate today) {
        if (paidOn != null || !today.isAfter(dueOn)) {
            return 0;
        }
        return ChronoUnit.DAYS.between(dueOn, today);
    }

    /** An available plot is allotted, not transferred; an owner with overdue instalments cannot transfer. */
    public static String transferBlock(String status, long overdueCount) {
        if (status == null || status.equals("AVAILABLE")) {
            return "plot is not allotted yet";
        }
        if (overdueCount > 0) {
            return overdueCount + " instalment(s) overdue";
        }
        return null;
    }
}
