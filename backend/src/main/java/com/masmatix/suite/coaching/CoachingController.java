package com.masmatix.suite.coaching;

import com.masmatix.suite.core.audit.AuditService;
import com.masmatix.suite.core.web.BusinessRuleException;
import com.masmatix.suite.core.web.NotFoundException;
import com.masmatix.suite.generated.coaching.Student;
import com.masmatix.suite.generated.coaching.StudentRepository;
import com.masmatix.suite.generated.coaching.StudyBatch;
import com.masmatix.suite.generated.coaching.StudyBatchRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Fee payments with a receipt, and the list of students whose fee is overdue. */
@RestController
@RequestMapping("/api/coaching/fees")
public class CoachingController {

    public record PayRequest(@NotNull @Min(1) @Max(12) Integer months) {}

    public record FeeReceipt(String receiptNumber, Instant paidAt, String student, String batch, int months,
                          BigDecimal monthlyFee, BigDecimal total, LocalDate paidFrom, LocalDate paidUntil) {}

    public record OverdueFee(Student student, int monthsOwed, BigDecimal amountOwed) {}

    private final StudentRepository students;
    private final StudyBatchRepository batches;
    private final AuditService audit;
    private final Clock clock;

    public CoachingController(StudentRepository students, StudyBatchRepository batches, AuditService audit, Clock clock) {
        this.students = students;
        this.batches = batches;
        this.audit = audit;
        this.clock = clock;
    }

    private StudyBatch batchOf(Student s) {
        return batches.findAll().stream().filter(b -> b.getName().equalsIgnoreCase(s.getBatchName())).findFirst()
                .orElseThrow(() -> new BusinessRuleException("batch " + s.getBatchName() + " not found for " + s.getFullName()));
    }

    @PostMapping("/students/{id}/pay")
    @Transactional
    public FeeReceipt pay(@PathVariable Long id, @Valid @RequestBody PayRequest req) {
        Student s = students.findById(id).orElseThrow(() -> new NotFoundException("student " + id + " not found"));
        StudyBatch b = batchOf(s);
        if (b.getMonthlyFee() == null) {
            throw new BusinessRuleException("batch " + b.getName() + " has no monthly fee");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate from = s.getFeePaidUntil() == null ? today : s.getFeePaidUntil();
        s.setFeePaidUntil(CoachingRules.paidUntilAfter(s.getFeePaidUntil(), today, req.months()));
        BigDecimal total = CoachingRules.amount(b.getMonthlyFee(), req.months());
        String number = "F" + id + "-" + s.getFeePaidUntil().toString().replace("-", "");
        audit.record("FEE", "student", s.getId(), number + " " + req.months() + " month(s) " + total);
        return new FeeReceipt(number, Instant.now(clock), s.getFullName(), b.getName(), req.months(), b.getMonthlyFee(),
                total, from, s.getFeePaidUntil());
    }

    @GetMapping("/overdue")
    @Transactional(readOnly = true)
    public List<OverdueFee> overdue() {
        LocalDate today = LocalDate.now(clock);
        return students.findAll().stream()
                .filter(s -> CoachingRules.monthsOwed(s.getFeePaidUntil(), today) > 0)
                .sorted(Comparator.comparing(Student::getFeePaidUntil))
                .map(s -> {
                    int owed = CoachingRules.monthsOwed(s.getFeePaidUntil(), today);
                    BigDecimal fee = batches.findAll().stream().filter(b -> b.getName().equalsIgnoreCase(s.getBatchName()))
                            .map(StudyBatch::getMonthlyFee).findFirst().orElse(null);
                    return new OverdueFee(s, owed, fee == null ? null : CoachingRules.amount(fee, owed));
                })
                .toList();
    }
}
