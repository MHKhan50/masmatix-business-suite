package com.masmatix.suite.imei;

import java.time.LocalDate;

/** Pure IMEI and warranty rules. frontend/src/lib/shops.ts uses the same rules. */
public final class ImeiRules {

    private ImeiRules() {}

    /** Luhn check digit for the first 14 digits of an IMEI. */
    public static int checkDigit(String first14) {
        if (first14 == null || !first14.matches("\\d{14}")) {
            throw new IllegalArgumentException("need exactly 14 digits");
        }
        int sum = 0;
        for (int i = 0; i < 14; i++) {
            int d = first14.charAt(13 - i) - '0';
            if (i % 2 == 0) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
        }
        return (10 - sum % 10) % 10;
    }

    /** 15 digits whose last digit is the Luhn check digit of the first 14. Catches most typing slips. */
    public static boolean isValid(String imei) {
        return imei != null && imei.matches("\\d{15}")
                && checkDigit(imei.substring(0, 14)) == imei.charAt(14) - '0';
    }

    /** Last day of cover: 12 months from 2 Aug 2026 ends on 1 Aug 2027. Null when there is no cover. */
    public static LocalDate warrantyEnd(LocalDate soldOn, Integer months) {
        if (soldOn == null || months == null || months <= 0) {
            return null;
        }
        return soldOn.plusMonths(months).minusDays(1);
    }

    public static boolean inWarranty(LocalDate soldOn, Integer months, LocalDate today) {
        LocalDate end = warrantyEnd(soldOn, months);
        return end != null && !today.isBefore(soldOn) && !today.isAfter(end);
    }
}
