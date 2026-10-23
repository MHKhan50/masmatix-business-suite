package com.masmatix.suite.gym;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** Pure membership rules: who may enter, and the new expiry after paying fees. */
public final class MembershipRules {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public record Decision(boolean allowed, String reason, long daysLeft) {}

    private MembershipRules() {}

    /** A membership is valid up to and including its expiry date. */
    public static Decision entry(LocalDate expiresOn, boolean frozen, LocalDate today) {
        if (frozen) {
            return new Decision(false, "Membership is frozen", 0);
        }
        if (expiresOn == null) {
            return new Decision(false, "No paid membership yet", 0);
        }
        if (today.isAfter(expiresOn)) {
            return new Decision(false, "Expired on " + DMY.format(expiresOn), 0);
        }
        long left = ChronoUnit.DAYS.between(today, expiresOn);
        return new Decision(true, left == 0 ? "Last day of membership" : "OK", left);
    }

    /**
     * Paying early extends from the current expiry, so no paid days are lost.
     * Paying after expiry starts from today, so the gap is not charged.
     */
    public static LocalDate renewedExpiry(LocalDate currentExpiry, LocalDate today, int months) {
        if (months < 1) {
            throw new IllegalArgumentException("months must be at least 1");
        }
        LocalDate base = currentExpiry == null || currentExpiry.isBefore(today) ? today : currentExpiry;
        return base.plusMonths(months);
    }
}
