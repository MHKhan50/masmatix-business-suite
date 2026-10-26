package com.masmatix.suite.library;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Loan period and late fines. frontend/src/lib/services.ts mirrors the fine. */
public final class LibraryRules {

    public static final int LOAN_DAYS = 14;
    public static final BigDecimal FINE_PER_DAY = new BigDecimal("0.20");
    public static final BigDecimal FINE_CAP = new BigDecimal("10.00");

    private LibraryRules() {}

    public static LocalDate dueOn(LocalDate issuedOn) {
        return issuedOn.plusDays(LOAN_DAYS);
    }

    /** Returned on the due date: no fine. Each day after adds 0.20, up to 10.00 per loan. */
    public static BigDecimal fine(LocalDate dueOn, LocalDate returnedOn) {
        long late = ChronoUnit.DAYS.between(dueOn, returnedOn);
        if (late <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return FINE_PER_DAY.multiply(BigDecimal.valueOf(late)).min(FINE_CAP).setScale(2, RoundingMode.HALF_UP);
    }

    public static int available(int copies, long onLoan) {
        return (int) Math.max(0, copies - onLoan);
    }
}
