package com.masmatix.suite.coaching;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Monthly tuition fees. Unlike a gym, a missed month is still owed (the student stayed in the batch),
 * so paying always extends from the old paid-until date.
 */
public final class CoachingRules {

    private CoachingRules() {}

    public static LocalDate paidUntilAfter(LocalDate paidUntil, LocalDate today, int months) {
        if (months < 1 || months > 12) {
            throw new IllegalArgumentException("months must be 1 to 12");
        }
        LocalDate base = paidUntil == null ? today : paidUntil;
        return base.plusMonths(months);
    }

    /** Whole or part months not yet paid: paid until 1 Oct, today 2 Oct is 1 month owed; 1 Nov or later is 2. */
    public static int monthsOwed(LocalDate paidUntil, LocalDate today) {
        if (paidUntil == null || !today.isAfter(paidUntil)) {
            return 0;
        }
        long whole = ChronoUnit.MONTHS.between(paidUntil, today);
        return (int) whole + 1;
    }

    public static BigDecimal amount(BigDecimal monthlyFee, int months) {
        return monthlyFee.multiply(BigDecimal.valueOf(months)).setScale(2, RoundingMode.HALF_UP);
    }
}
