package com.masmatix.suite;

import static org.assertj.core.api.Assertions.assertThat;

import com.masmatix.suite.clinic.AllergyCheck;
import com.masmatix.suite.gym.MembershipRules;
import com.masmatix.suite.hotel.StayRules;
import com.masmatix.suite.salon.SlotPlanner;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Plain unit tests for the rules behind the salon, clinic, gym and hotel systems. */
class BusinessRulesTest {

    private static final LocalDate DAY = LocalDate.of(2030, 3, 4);

    // ---------------------------------------------------------------- salon

    @Test
    void backToBackAppointmentsDoNotOverlap() {
        LocalDateTime ten = DAY.atTime(10, 0);
        assertThat(SlotPlanner.overlaps(ten, ten.plusMinutes(45), ten.plusMinutes(45), ten.plusMinutes(60))).isFalse();
        assertThat(SlotPlanner.overlaps(ten, ten.plusMinutes(45), ten.plusMinutes(30), ten.plusMinutes(60))).isTrue();
    }

    @Test
    void treatmentMustFinishByClosingTime() {
        assertThat(SlotPlanner.withinOpeningHours(DAY.atTime(17, 15), DAY.atTime(18, 0))).isTrue();
        assertThat(SlotPlanner.withinOpeningHours(DAY.atTime(17, 30), DAY.atTime(18, 15))).isFalse();
        assertThat(SlotPlanner.withinOpeningHours(DAY.atTime(8, 45), DAY.atTime(9, 30))).isFalse();
    }

    @Test
    void freeSlotsSkipBusyTimeAndThePast() {
        List<SlotPlanner.Interval> busy = List.of(new SlotPlanner.Interval(DAY.atTime(10, 0), DAY.atTime(10, 45)));
        List<LocalTime> free = SlotPlanner.freeStarts(DAY, 45, busy, DAY.atTime(9, 10));
        assertThat(free).startsWith(LocalTime.of(9, 15)).contains(LocalTime.of(10, 45)).endsWith(LocalTime.of(17, 15));
        assertThat(free).doesNotContain(LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 0), LocalTime.of(10, 30));
        // 09:00 to 17:15 is 34 quarter-hour starts; 09:00 is past and 09:30 to 10:30 (5 starts) clash.
        assertThat(free).hasSize(34 - 1 - 5);
    }

    // ---------------------------------------------------------------- clinic

    @Test
    void allergyMatchesIngredientCaseInsensitive() {
        assertThat(AllergyCheck.firstMatch("Penicillin, nuts", "Amoxicillin (sample)", "PENICILLIN")).contains("penicillin");
        assertThat(AllergyCheck.firstMatch("Penicillin", "Paracetamol (sample)", "paracetamol")).isEmpty();
    }

    @Test
    void emptyOrNoneAllergiesNeverMatch() {
        assertThat(AllergyCheck.firstMatch(null, "anything")).isEmpty();
        assertThat(AllergyCheck.firstMatch("none", "nonesuch")).isEmpty();
        assertThat(AllergyCheck.allergyTerms("Latex; ibuprofen\nNKDA")).containsExactly("latex", "ibuprofen");
    }

    // ---------------------------------------------------------------- gym

    @Test
    void membershipIsValidUpToAndIncludingExpiryDay() {
        assertThat(MembershipRules.entry(DAY, false, DAY).allowed()).isTrue();
        assertThat(MembershipRules.entry(DAY, false, DAY.plusDays(1)).allowed()).isFalse();
        assertThat(MembershipRules.entry(DAY, false, DAY.plusDays(1)).reason()).isEqualTo("Expired on 04/03/2030");
        assertThat(MembershipRules.entry(DAY.plusDays(10), true, DAY).allowed()).isFalse();
        assertThat(MembershipRules.entry(null, false, DAY).allowed()).isFalse();
        assertThat(MembershipRules.entry(DAY.plusDays(10), false, DAY).daysLeft()).isEqualTo(10);
    }

    @Test
    void earlyRenewalKeepsPaidDaysLateRenewalStartsToday() {
        assertThat(MembershipRules.renewedExpiry(DAY.plusDays(5), DAY, 1)).isEqualTo(LocalDate.of(2030, 4, 9));
        assertThat(MembershipRules.renewedExpiry(DAY.minusDays(20), DAY, 1)).isEqualTo(LocalDate.of(2030, 4, 4));
        assertThat(MembershipRules.renewedExpiry(null, DAY, 12)).isEqualTo(LocalDate.of(2031, 3, 4));
        // Month ends: 31 January plus one month is the last day of February.
        assertThat(MembershipRules.renewedExpiry(LocalDate.of(2030, 1, 31), LocalDate.of(2030, 1, 20), 1))
                .isEqualTo(LocalDate.of(2030, 2, 28));
    }

    // ---------------------------------------------------------------- hotel

    @Test
    void checkoutDayIsFreeForTheNextGuest() {
        LocalDate in = DAY;
        LocalDate out = DAY.plusDays(2);
        assertThat(StayRules.nights(in, out)).isEqualTo(2);
        assertThat(StayRules.overlaps(in, out, out, out.plusDays(1))).isFalse();
        assertThat(StayRules.overlaps(in, out, in.plusDays(1), out.plusDays(1))).isTrue();
    }

    @Test
    void stayTotalAndDateChecks() {
        assertThat(StayRules.total(new BigDecimal("85.00"), 2)).isEqualByComparingTo("170.00");
        assertThat(StayRules.total(new BigDecimal("64.995"), 3)).isEqualByComparingTo("194.99");
        assertThat(StayRules.checkDates(DAY, DAY, DAY)).isEqualTo("Check-out must be after check-in");
        assertThat(StayRules.checkDates(DAY.minusDays(1), DAY, DAY)).isEqualTo("Check-in date is in the past");
        assertThat(StayRules.checkDates(DAY, DAY.plusDays(31), DAY)).isEqualTo("Stays are limited to 30 nights");
        assertThat(StayRules.checkDates(DAY, DAY.plusDays(1), DAY)).isNull();
    }
}
