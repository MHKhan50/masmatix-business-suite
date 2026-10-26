package com.masmatix.suite.payroll;

import com.masmatix.suite.core.audit.AuditService;
import com.masmatix.suite.core.web.BusinessRuleException;
import com.masmatix.suite.core.web.NotFoundException;
import com.masmatix.suite.generated.payroll.AttendanceEntry;
import com.masmatix.suite.generated.payroll.AttendanceEntryRepository;
import com.masmatix.suite.generated.payroll.Employee;
import com.masmatix.suite.generated.payroll.EmployeeRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Check-in with late minutes and fine worked out, and a monthly pay summary. */
@RestController
@RequestMapping("/api/payroll/desk")
public class PayrollController {

    /** checkIn as HH:mm. */
    public record CheckInRequest(@NotBlank String employeeCode, @NotBlank String checkIn) {}

    /** Minutes worked after the shift, added at check-out. */
    public record OvertimeRequest(@NotNull @Min(0) @Max(720) Integer minutes) {}

    public record Payslip(Employee employee, YearMonth month, int daysPresent, int minutesLate, BigDecimal fines,
                          int overtimeMinutes, BigDecimal overtimePay, BigDecimal salary, BigDecimal net) {}

    private final EmployeeRepository employees;
    private final AttendanceEntryRepository attendance;
    private final AuditService audit;
    private final Clock clock;

    public PayrollController(EmployeeRepository employees, AttendanceEntryRepository attendance, AuditService audit, Clock clock) {
        this.employees = employees;
        this.attendance = attendance;
        this.audit = audit;
        this.clock = clock;
    }

    private Employee employee(String code) {
        Employee e = employees.findAll().stream().filter(x -> x.getEmployeeCode().equalsIgnoreCase(code.trim())).findFirst()
                .orElseThrow(() -> new NotFoundException("employee " + code + " not found"));
        if (e.getMonthlySalary() == null) {
            throw new BusinessRuleException("employee " + e.getEmployeeCode() + " has no monthly salary");
        }
        return e;
    }

    @PostMapping("/check-in")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AttendanceEntry checkIn(@Valid @RequestBody CheckInRequest req) {
        Employee e = employee(req.employeeCode());
        LocalTime at;
        try {
            at = LocalTime.parse(req.checkIn());
        } catch (DateTimeParseException ex) {
            throw new BusinessRuleException("check-in time must be HH:mm, e.g. 09:12");
        }
        if (e.getShiftStart() == null || !e.getShiftStart().matches("\\d{2}:\\d{2}")) {
            throw new BusinessRuleException("employee " + e.getEmployeeCode() + " has no shift start (HH:mm)");
        }
        LocalDate today = LocalDate.now(clock);
        boolean already = attendance.findAll().stream()
                .anyMatch(a -> a.getEmployeeCode().equalsIgnoreCase(e.getEmployeeCode()) && today.equals(a.getWorkDate()));
        if (already) {
            throw new BusinessRuleException(e.getEmployeeCode() + " already checked in today");
        }
        int late;
        try {
            late = PayrollRules.minutesLate(e.getShiftStart(), at);
        } catch (DateTimeParseException ex) {
            throw new BusinessRuleException("employee " + e.getEmployeeCode() + " has a bad shift start " + e.getShiftStart());
        }
        AttendanceEntry a = new AttendanceEntry();
        a.setEmployeeCode(e.getEmployeeCode());
        a.setWorkDate(today);
        a.setMinutesLate(late);
        a.setOvertimeMinutes(0);
        a.setFineAmount(PayrollRules.lateFine(e.getMonthlySalary(), late));
        AttendanceEntry saved = attendance.save(a);
        audit.record("CHECK_IN", "attendance", saved.getId(), e.getEmployeeCode() + " at " + at + " late " + late + " fine " + saved.getFineAmount());
        return saved;
    }

    @PostMapping("/attendance/{id}/overtime")
    @Transactional
    public AttendanceEntry overtime(@PathVariable Long id, @Valid @RequestBody OvertimeRequest req) {
        AttendanceEntry a = attendance.findById(id).orElseThrow(() -> new NotFoundException("attendance " + id + " not found"));
        a.setOvertimeMinutes(req.minutes());
        audit.record("OVERTIME", "attendance", a.getId(), a.getEmployeeCode() + " " + a.getWorkDate() + " " + req.minutes() + " min");
        return a;
    }

    /** month as yyyy-MM. Fines are re-worked from late minutes so a hand-typed fine cannot drift from the rule. */
    @GetMapping("/payslip")
    @Transactional(readOnly = true)
    public Payslip payslip(@RequestParam String employeeCode, @RequestParam String month) {
        Employee e = employee(employeeCode);
        YearMonth ym;
        try {
            ym = YearMonth.parse(month);
        } catch (DateTimeParseException ex) {
            throw new BusinessRuleException("month must be yyyy-MM");
        }
        List<AttendanceEntry> rows = attendance.findAll().stream()
                .filter(a -> a.getEmployeeCode().equalsIgnoreCase(e.getEmployeeCode()) && a.getWorkDate() != null
                        && YearMonth.from(a.getWorkDate()).equals(ym)).toList();
        int late = 0;
        int ot = 0;
        BigDecimal fines = BigDecimal.ZERO.setScale(2);
        for (AttendanceEntry a : rows) {
            int m = a.getMinutesLate() == null ? 0 : a.getMinutesLate();
            late += m;
            ot += a.getOvertimeMinutes() == null ? 0 : a.getOvertimeMinutes();
            fines = fines.add(PayrollRules.lateFine(e.getMonthlySalary(), m));
        }
        BigDecimal otPay = PayrollRules.overtimePay(e.getMonthlySalary(), ot);
        BigDecimal salary = e.getMonthlySalary().setScale(2);
        return new Payslip(e, ym, rows.size(), late, fines, ot, otPay, salary, salary.subtract(fines).add(otPay));
    }
}
