package com.masmatix.suite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masmatix.suite.pos.PricingCalculator;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PricingCalculatorTest {

    @Test
    void taxIsRoundedPerLineHalfUp() {
        // 3 x 0.75 = 2.25 net; 20% = 0.45
        PricingCalculator.Line a = PricingCalculator.line(new BigDecimal("0.75"), new BigDecimal("20.00"), 3);
        assertThat(a.net()).isEqualByComparingTo("2.25");
        assertThat(a.tax()).isEqualByComparingTo("0.45");
        // 1 x 1.25 at 20% = 0.25 exactly; 1 x 0.99 at 20% = 0.198 -> 0.20
        PricingCalculator.Line b = PricingCalculator.line(new BigDecimal("0.99"), new BigDecimal("20.00"), 1);
        assertThat(b.tax()).isEqualByComparingTo("0.20");
    }

    @Test
    void zeroRatedItemsHaveNoTax() {
        PricingCalculator.Line milk = PricingCalculator.line(new BigDecimal("1.45"), BigDecimal.ZERO, 2);
        assertThat(milk.tax()).isEqualByComparingTo("0.00");
    }

    @Test
    void totalsAddUp() {
        PricingCalculator.Totals t = PricingCalculator.totals(List.of(
                PricingCalculator.line(new BigDecimal("1.45"), BigDecimal.ZERO, 2),
                PricingCalculator.line(new BigDecimal("0.90"), new BigDecimal("20.00"), 3)));
        assertThat(t.subtotal()).isEqualByComparingTo("5.60");
        assertThat(t.tax()).isEqualByComparingTo("0.54");
        assertThat(t.total()).isEqualByComparingTo("6.14");
    }

    @Test
    void rejectsZeroQuantity() {
        assertThatThrownBy(() -> PricingCalculator.line(BigDecimal.ONE, BigDecimal.ZERO, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
