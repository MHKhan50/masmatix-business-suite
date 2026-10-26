package com.masmatix.suite.lawfirm;

import java.time.LocalDate;

/** When a new hearing date may be added to a case. */
public final class HearingRules {

    private HearingRules() {}

    /** Returns why the date is refused, or null when it can be added. */
    public static String refuse(String caseStatus, LocalDate hearingOn, LocalDate today, boolean sameDayExists) {
        if ("DECIDED".equals(caseStatus) || "CLOSED".equals(caseStatus)) {
            return "case is " + caseStatus.toLowerCase();
        }
        if (hearingOn.isBefore(today)) {
            return "hearing date is in the past";
        }
        if (sameDayExists) {
            return "this case already has a hearing on " + hearingOn;
        }
        return null;
    }
}
