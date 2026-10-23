package com.masmatix.suite.hotel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Pure stay rules. Dates are nights: a stay from the 10th to the 12th is two nights and frees the room on the 12th. */
public final class StayRules {

    public static final int MAX_NIGHTS = 30;

    private StayRules() {}

    public static long nights(LocalDate checkIn, LocalDate checkOut) {
        return ChronoUnit.DAYS.between(checkIn, checkOut);
    }

    /** Half-open date ranges, so one guest can check out on the day the next guest checks in. */
    public static boolean overlaps(LocalDate aIn, LocalDate aOut, LocalDate bIn, LocalDate bOut) {
        return aIn.isBefore(bOut) && bIn.isBefore(aOut);
    }

    public static BigDecimal total(BigDecimal nightlyRate, long nights) {
        return nightlyRate.multiply(BigDecimal.valueOf(nights)).setScale(2, RoundingMode.HALF_UP);
    }

    /** Returns null when the dates are fine, otherwise the reason. */
    public static String checkDates(LocalDate checkIn, LocalDate checkOut, LocalDate today) {
        if (!checkOut.isAfter(checkIn)) {
            return "Check-out must be after check-in";
        }
        if (checkIn.isBefore(today)) {
            return "Check-in date is in the past";
        }
        if (nights(checkIn, checkOut) > MAX_NIGHTS) {
            return "Stays are limited to " + MAX_NIGHTS + " nights";
        }
        return null;
    }
}
