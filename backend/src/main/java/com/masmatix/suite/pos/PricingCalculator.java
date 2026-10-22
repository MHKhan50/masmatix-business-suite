package com.masmatix.suite.pos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Money maths for the till. Tax is rounded per line (half-up, 2 dp), then summed.
 * frontend/src/lib/cart.ts uses the same rule so the screen and receipt always agree.
 */
public final class PricingCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public record Line(BigDecimal net, BigDecimal tax) {}
    public record Totals(BigDecimal subtotal, BigDecimal tax, BigDecimal total) {}

    private PricingCalculator() {}

    public static Line line(BigDecimal unitPrice, BigDecimal taxRatePercent, int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be positive");
        }
        BigDecimal net = unitPrice.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tax = net.multiply(taxRatePercent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return new Line(net, tax);
    }

    public static Totals totals(List<Line> lines) {
        BigDecimal sub = BigDecimal.ZERO.setScale(2);
        BigDecimal tax = BigDecimal.ZERO.setScale(2);
        for (Line l : lines) {
            sub = sub.add(l.net());
            tax = tax.add(l.tax());
        }
        return new Totals(sub, tax, sub.add(tax));
    }
}
