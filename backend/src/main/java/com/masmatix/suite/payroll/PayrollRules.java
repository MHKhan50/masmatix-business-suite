package com.masmatix.suite.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalTime;

/**
 * Sample attendance rules (invented defaults, set per business): 10 minutes' grace, fines and overtime
 * at the per-minute rate of a 26-day, 8-hour month, overtime at 1.5 times. Check local employment law before use.
 */
public final class PayrollRules {

    public static final int GRACE_MINUTES = 10;
    public static final int WORK_DAYS = 26;
    public static final int MINUTES_PER_DAY = 8 * 60;
    public static final BigDecimal OVERTIME_FACTOR = new BigDecimal("1.5");

    private PayrollRules() {}

    public static int minutesLate(String shiftStart, LocalTime checkIn) {
        long m = Duration.between(LocalTime.parse(shiftStart), checkIn).toMinutes();
        return (int) Math.max(0, m);
    }

    public static BigDecimal perMinute(BigDecimal monthlySalary) {
        return monthlySalary.divide(BigDecimal.valueOf((long) WORK_DAYS * MINUTES_PER_DAY), 6, RoundingMode.HALF_UP);
    }

    /** Inside the grace period: no fine. After it, every late minute counts, not only those past the grace. */
    public static BigDecimal lateFine(BigDecimal monthlySalary, int minutesLate) {
        if (minutesLate <= GRACE_MINUTES) {
            return BigDecimal.ZERO.setScale(2);
        }
        return perMinute(monthlySalary).multiply(BigDecimal.valueOf(minutesLate)).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal overtimePay(BigDecimal monthlySalary, int overtimeMinutes) {
        if (overtimeMinutes <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return perMinute(monthlySalary).multiply(OVERTIME_FACTOR).multiply(BigDecimal.valueOf(overtimeMinutes))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
