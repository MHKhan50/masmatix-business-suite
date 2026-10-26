package com.masmatix.suite.tailoring;

import java.math.BigDecimal;
import java.util.List;

/** Order stages and money owed (never below zero, even if more was taken as advance). frontend/src/lib/services.ts mirrors balanceDue. */
public final class TailoringRules {

    public static final List<String> STAGES = List.of("CUTTING", "STITCHING", "TRIAL", "READY", "DELIVERED");

    private TailoringRules() {}

    public static BigDecimal balanceDue(BigDecimal price, BigDecimal advancePaid) {
        BigDecimal paid = advancePaid == null ? BigDecimal.ZERO : advancePaid;
        return price.subtract(paid).max(BigDecimal.ZERO).setScale(2);
    }

    /** The next stage, or null when the order is already delivered. Delivery is its own step (it takes the balance). */
    public static String next(String stage) {
        int i = STAGES.indexOf(stage == null ? "CUTTING" : stage);
        if (i < 0 || i >= STAGES.size() - 2) {
            return null;
        }
        return STAGES.get(i + 1);
    }

}
