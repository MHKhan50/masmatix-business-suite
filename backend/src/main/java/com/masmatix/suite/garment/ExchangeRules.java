package com.masmatix.suite.garment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Pure exchange rules. frontend/src/lib/shops.ts uses the same window and difference. */
public final class ExchangeRules {

    /** Days after the sale in which an item can be exchanged, counting the sale day as day 0. */
    public static final int WINDOW_DAYS = 30;

    private ExchangeRules() {}

    public static boolean withinWindow(LocalDate soldOn, LocalDate today) {
        long days = ChronoUnit.DAYS.between(soldOn, today);
        return days >= 0 && days <= WINDOW_DAYS;
    }

    /** New price minus the price paid: positive means the customer pays more, negative means a refund. */
    public static BigDecimal difference(BigDecimal paidUnitPrice, BigDecimal newPrice) {
        return newPrice.subtract(paidUnitPrice).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** A faulty item goes to the supplier pile, not back on the shelf. */
    public static boolean returnsToStock(String reason) {
        return !"FAULT".equals(reason);
    }
}
