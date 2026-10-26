package com.masmatix.suite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.masmatix.suite.coaching.CoachingRules;
import com.masmatix.suite.construction.ConstructionRules;
import com.masmatix.suite.housing.HousingRules;
import com.masmatix.suite.lab.LabRules;
import com.masmatix.suite.lab.LabRules.Flag;
import com.masmatix.suite.lawfirm.HearingRules;
import com.masmatix.suite.library.LibraryRules;
import com.masmatix.suite.microfinance.LoanRules;
import com.masmatix.suite.payroll.PayrollRules;
import com.masmatix.suite.tailoring.TailoringRules;
import com.masmatix.suite.travel.TravelRules;
import com.masmatix.suite.weighbridge.WeighbridgeRules;
import com.masmatix.suite.wholesale.WholesaleRules;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure rules for the twelve service and trade systems. frontend/src/lib/services.test.ts uses the same numbers. */
class ServiceRulesTest {

    /** A Monday. */
    private static final LocalDate D = LocalDate.of(2030, 3, 4);

    private static BigDecimal m(String v) {
        return new BigDecimal(v);
    }

    @Test
    void libraryFineStartsTheDayAfterDueAndIsCapped() {
        assertEquals(LocalDate.of(2030, 3, 18), LibraryRules.dueOn(D));
        assertEquals(m("0.00"), LibraryRules.fine(D, D));
        assertEquals(m("0.00"), LibraryRules.fine(D, D.minusDays(2)));
        assertEquals(m("0.60"), LibraryRules.fine(D, D.plusDays(3)));
        assertEquals(m("10.00"), LibraryRules.fine(D, D.plusDays(80)));
        assertEquals(0, LibraryRules.available(3, 3));
        assertEquals(2, LibraryRules.available(3, 1));
    }

    @Test
    void coachingFeesCountMissedMonthsAndExtendFromTheOldDate() {
        assertEquals(0, CoachingRules.monthsOwed(null, D));
        assertEquals(0, CoachingRules.monthsOwed(D, D));
        assertEquals(1, CoachingRules.monthsOwed(D, D.plusDays(1)));
        assertEquals(2, CoachingRules.monthsOwed(D, D.plusMonths(1)));
        assertEquals(LocalDate.of(2030, 4, 1), CoachingRules.paidUntilAfter(LocalDate.of(2030, 2, 1), D, 2));
        assertEquals(LocalDate.of(2030, 4, 4), CoachingRules.paidUntilAfter(null, D, 1));
        assertEquals(m("120.00"), CoachingRules.amount(m("40.00"), 3));
        assertThrows(IllegalArgumentException.class, () -> CoachingRules.paidUntilAfter(D, D, 0));
    }

    @Test
    void labFlagsResultsAgainstTheRange() {
        assertEquals(Flag.NORMAL, LabRules.flag("15.2", "13.0-17.0 g/dL (sample)"));
        assertEquals(Flag.LOW, LabRules.flag("11.0", "13.0-17.0"));
        assertEquals(Flag.HIGH, LabRules.flag("18 g/dL", "13.0 - 17.0"));
        assertEquals(Flag.HIGH, LabRules.flag("5.9", "< 5.7"));
        assertEquals(Flag.NORMAL, LabRules.flag("70", "> 60"));
        assertEquals(Flag.NOT_NUMERIC, LabRules.flag("negative", "13.0-17.0"));
        assertEquals(Flag.NOT_NUMERIC, LabRules.flag("15", "see lab reference"));
        assertTrue(LabRules.canMove("COLLECTED", "IN_PROGRESS"));
        assertTrue(LabRules.canMove(null, "REPORTED"));
        assertFalse(LabRules.canMove("IN_PROGRESS", "IN_PROGRESS"));
        assertFalse(LabRules.canMove("REPORTED", "REPORTED"));
    }

    @Test
    void tailoringBalanceAndStages() {
        assertEquals(m("25.00"), TailoringRules.balanceDue(m("45.00"), m("20.00")));
        assertEquals(m("45.00"), TailoringRules.balanceDue(m("45.00"), null));
        assertEquals(m("0.00"), TailoringRules.balanceDue(m("45.00"), m("50.00")));
        assertEquals("STITCHING", TailoringRules.next(null));
        assertEquals("READY", TailoringRules.next("TRIAL"));
        assertNull(TailoringRules.next("READY"));
        assertNull(TailoringRules.next("DELIVERED"));
    }

    @Test
    void travelChecklistAndReminderWindow() {
        assertEquals(List.of(), TravelRules.missing("UMRAH", true, "APPROVED", m("1450.00"), m("1450.00")));
        assertEquals(List.of("Passport not received", "Visa not approved yet", "Balance due 950.00"),
                TravelRules.missing("UMRAH", false, "SUBMITTED", m("1450.00"), m("500.00")));
        assertEquals(List.of(), TravelRules.missing("FLIGHT", true, "NOT_STARTED", m("300.00"), m("300.00")));
        assertEquals(List.of("Visa refused"), TravelRules.missing("TOUR", true, "REFUSED", null, null));
        List<String> gap = List.of("Visa not approved yet");
        assertTrue(TravelRules.needsReminder(D.plusDays(14), D, gap));
        assertFalse(TravelRules.needsReminder(D.plusDays(15), D, gap));
        assertFalse(TravelRules.needsReminder(D.minusDays(1), D, gap));
        assertFalse(TravelRules.needsReminder(D, D, List.of()));
    }

    @Test
    void wholesaleRouteDayAndOrderFlow() {
        assertTrue(WholesaleRules.runsOn("MON", D));
        assertFalse(WholesaleRules.runsOn("THU", D));
        assertTrue(WholesaleRules.canMove("BOOKED", "LOADED"));
        assertTrue(WholesaleRules.canMove("LOADED", "DELIVERED"));
        assertFalse(WholesaleRules.canMove("BOOKED", "DELIVERED"));
        assertFalse(WholesaleRules.canMove("LOADED", "CANCELLED"));
    }

    @Test
    void weighbridgeNetWeightAndSlipNumbers() {
        assertEquals(15730, WeighbridgeRules.netKg(24850, 9120));
        IllegalArgumentException swapped = assertThrows(IllegalArgumentException.class, () -> WeighbridgeRules.netKg(9120, 24850));
        assertTrue(swapped.getMessage().contains("swapped"));
        assertThrows(IllegalArgumentException.class, () -> WeighbridgeRules.netKg(60001, 9000));
        assertThrows(IllegalArgumentException.class, () -> WeighbridgeRules.netKg(9000, 0));
        assertEquals("WB-010246", WeighbridgeRules.nextSlip("WB-010245"));
        assertEquals("WB-000001", WeighbridgeRules.nextSlip(null));
    }

    @Test
    void constructionCostLines() {
        assertEquals(m("42.50"), ConstructionRules.materialCost(m("12.5"), m("3.40")));
        assertEquals(m("150.00"), ConstructionRules.labourCost(6, m("25.00")));
        assertEquals(m("0.00"), ConstructionRules.materialCost(null, m("3.40")));
    }

    @Test
    void housingOverdueAndTransferBlock() {
        assertEquals(0, HousingRules.daysLate(D, null, D));
        assertEquals(3, HousingRules.daysLate(D, null, D.plusDays(3)));
        assertEquals(0, HousingRules.daysLate(D, D.plusDays(1), D.plusDays(5)));
        assertEquals("plot is not allotted yet", HousingRules.transferBlock("AVAILABLE", 0));
        assertEquals("1 instalment(s) overdue", HousingRules.transferBlock("ALLOTTED", 1));
        assertNull(HousingRules.transferBlock("TRANSFERRED", 0));
    }

    @Test
    void payrollGraceFineAndOvertime() {
        assertEquals(12, PayrollRules.minutesLate("09:00", LocalTime.of(9, 12)));
        assertEquals(0, PayrollRules.minutesLate("09:00", LocalTime.of(8, 55)));
        assertEquals(m("0.00"), PayrollRules.lateFine(m("1800.00"), 10));
        // 1800 / (26 x 480) = 0.144231 per minute; 15 minutes = 2.16.
        assertEquals(m("2.16"), PayrollRules.lateFine(m("1800.00"), 15));
        assertEquals(m("12.98"), PayrollRules.overtimePay(m("1800.00"), 60));
        assertEquals(m("0.00"), PayrollRules.overtimePay(m("1800.00"), 0));
    }

    @Test
    void loanScheduleArrearsAndLateFee() {
        List<LoanRules.Due> s = LoanRules.schedule(LocalDate.of(2030, 1, 15), 3, m("100.00"));
        assertEquals(LocalDate.of(2030, 2, 15), s.get(0).dueOn());
        assertEquals(LocalDate.of(2030, 4, 15), s.get(2).dueOn());
        assertEquals(m("100.00"), LoanRules.dueSoFar(s, D));
        assertEquals(m("60.00"), LoanRules.arrears(m("100.00"), m("40.00")));
        assertEquals(m("0.00"), LoanRules.arrears(m("100.00"), m("150.00")));
        assertEquals(m("1.20"), LoanRules.lateFee(m("60.00")));
        assertEquals(m("0.00"), LoanRules.lateFee(m("0.00")));
    }

    @Test
    void hearingDatesRefusedForClosedCasesPastDatesAndDuplicates() {
        assertEquals("case is closed", HearingRules.refuse("CLOSED", D, D, false));
        assertEquals("hearing date is in the past", HearingRules.refuse("OPEN", D.minusDays(1), D, false));
        assertTrue(HearingRules.refuse("OPEN", D, D, true).startsWith("this case already has a hearing"));
        assertNull(HearingRules.refuse("ADJOURNED", D.plusDays(7), D, false));
    }
}
