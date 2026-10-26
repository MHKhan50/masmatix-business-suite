package com.masmatix.suite.microfinance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Flat monthly instalments, first one a month after the start date. A late fee (sample default 2% of the
 * arrears) is added when a repayment is taken while instalments are overdue. Set per lender.
 */
public final class LoanRules {

    public static final BigDecimal LATE_FEE_RATE = new BigDecimal("0.02");

    public record Due(int number, LocalDate dueOn, BigDecimal amount) {}

    private LoanRules() {}

    public static List<Due> schedule(LocalDate startsOn, int months, BigDecimal instalment) {
        List<Due> out = new ArrayList<>();
        for (int i = 1; i <= months; i++) {
            out.add(new Due(i, startsOn.plusMonths(i), instalment.setScale(2)));
        }
        return out;
    }

    /** Sum of instalments due on or before today. */
    public static BigDecimal dueSoFar(List<Due> schedule, LocalDate today) {
        return schedule.stream().filter(d -> !d.dueOn().isAfter(today)).map(Due::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
    }

    public static BigDecimal arrears(BigDecimal dueSoFar, BigDecimal paid) {
        return dueSoFar.subtract(paid).max(BigDecimal.ZERO).setScale(2);
    }

    public static BigDecimal lateFee(BigDecimal arrears) {
        return arrears.multiply(LATE_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
    }
}
