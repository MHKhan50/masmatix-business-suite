package com.masmatix.suite.construction;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Cost lines for the site dashboard, rounded half-up to 2 dp. */
public final class ConstructionRules {

    private ConstructionRules() {}

    public static BigDecimal materialCost(BigDecimal quantity, BigDecimal unitCost) {
        if (quantity == null || unitCost == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return quantity.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal labourCost(Integer workers, BigDecimal dailyRate) {
        if (workers == null || dailyRate == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return dailyRate.multiply(BigDecimal.valueOf(workers)).setScale(2, RoundingMode.HALF_UP);
    }
}
