package com.masmatix.suite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masmatix.suite.bakery.BakeryRules;
import com.masmatix.suite.bakery.BakeryRules.Status;
import com.masmatix.suite.garment.ExchangeRules;
import com.masmatix.suite.imei.ImeiRules;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure rules for the garment, IMEI and bakery systems. frontend/src/lib/shops.test.ts uses the same numbers. */
class ShopRulesTest {

    private static final LocalDate D = LocalDate.of(2030, 3, 4);

    @Test
    void imeiCheckDigitFollowsLuhn() {
        // 49015420323751-8 is the worked example in the GSMA IMEI guide.
        assertThat(ImeiRules.checkDigit("49015420323751")).isEqualTo(8);
        assertThat(ImeiRules.isValid("490154203237518")).isTrue();
        assertThat(ImeiRules.isValid("490154203237517")).isFalse();
        assertThat(ImeiRules.isValid("000000000000018")).isTrue();
        assertThat(ImeiRules.isValid("49015420323751")).isFalse();
        assertThat(ImeiRules.isValid("49015420323751A")).isFalse();
    }

    @Test
    void imeiCatchesSwappedNeighbourDigits() {
        assertThat(ImeiRules.isValid("940154203237518")).isFalse();
    }

    @Test
    void warrantyEndsTheDayBeforeTheSameDateNextYear() {
        assertThat(ImeiRules.warrantyEnd(LocalDate.of(2026, 8, 2), 12)).isEqualTo(LocalDate.of(2027, 8, 1));
        assertThat(ImeiRules.warrantyEnd(LocalDate.of(2030, 1, 31), 1)).isEqualTo(LocalDate.of(2030, 2, 27));
        assertThat(ImeiRules.warrantyEnd(D, 0)).isNull();
        assertThat(ImeiRules.inWarranty(LocalDate.of(2029, 3, 5), 12, D)).isTrue();
        assertThat(ImeiRules.inWarranty(LocalDate.of(2029, 3, 4), 12, D)).isFalse();
    }

    @Test
    void bakeryExpiryAndStatus() {
        assertThat(BakeryRules.expiresOn(LocalDate.of(2026, 10, 24), 3)).isEqualTo(LocalDate.of(2026, 10, 27));
        assertThat(BakeryRules.status(D.minusDays(1), D)).isEqualTo(Status.EXPIRED);
        assertThat(BakeryRules.status(D, D)).isEqualTo(Status.LAST_DAY);
        assertThat(BakeryRules.status(D.plusDays(1), D)).isEqualTo(Status.TOMORROW);
        assertThat(BakeryRules.status(D.plusDays(2), D)).isEqualTo(Status.OK);
    }

    @Test
    void bakerySellsOldestFreshBatchFirstAndSkipsExpired() {
        List<BakeryRules.Stock> stock = List.of(
                new BakeryRules.Stock(1, "OLD", D.minusDays(1), 10),
                new BakeryRules.Stock(2, "TODAY", D, 3),
                new BakeryRules.Stock(3, "LATER", D.plusDays(2), 20));
        List<BakeryRules.Take> takes = BakeryRules.allocate(stock, 5, D);
        assertThat(takes).containsExactly(new BakeryRules.Take(2, "TODAY", 3), new BakeryRules.Take(3, "LATER", 2));
        assertThatThrownBy(() -> BakeryRules.allocate(stock, 24, D)).hasMessage("Only 23 fresh in stock");
    }

    @Test
    void exchangeWindowAndDifference() {
        assertThat(ExchangeRules.withinWindow(D, D.plusDays(30))).isTrue();
        assertThat(ExchangeRules.withinWindow(D, D.plusDays(31))).isFalse();
        assertThat(ExchangeRules.withinWindow(D, D.minusDays(1))).isFalse();
        assertThat(ExchangeRules.difference(new BigDecimal("24.00"), new BigDecimal("18.50"))).isEqualByComparingTo("-5.50");
        assertThat(ExchangeRules.returnsToStock("SIZE")).isTrue();
        assertThat(ExchangeRules.returnsToStock("FAULT")).isFalse();
    }
}
