package com.masmatix.suite.travel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** What is still missing before a traveller can go. Flights alone need no visa from this agency. */
public final class TravelRules {

    public static final int REMIND_DAYS = 14;

    private TravelRules() {}

    public static List<String> missing(String tripType, Boolean passportReceived, String visaStatus,
                                       BigDecimal amount, BigDecimal paid) {
        List<String> out = new ArrayList<>();
        if (!Boolean.TRUE.equals(passportReceived)) {
            out.add("Passport not received");
        }
        if (!"FLIGHT".equals(tripType)) {
            if ("REFUSED".equals(visaStatus)) {
                out.add("Visa refused");
            } else if (!"APPROVED".equals(visaStatus)) {
                out.add("Visa not approved yet");
            }
        }
        BigDecimal p = paid == null ? BigDecimal.ZERO : paid;
        if (amount != null && p.compareTo(amount) < 0) {
            out.add("Balance due " + amount.subtract(p).setScale(2).toPlainString());
        }
        return out;
    }

    /** Days until travel; negative once the date has passed. */
    public static long daysLeft(LocalDate travelOn, LocalDate today) {
        return ChronoUnit.DAYS.between(today, travelOn);
    }

    public static boolean needsReminder(LocalDate travelOn, LocalDate today, List<String> missing) {
        long d = daysLeft(travelOn, today);
        return !missing.isEmpty() && d >= 0 && d <= REMIND_DAYS;
    }
}
