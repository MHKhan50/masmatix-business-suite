package com.masmatix.suite.microfinance;

import com.masmatix.suite.core.audit.AuditService;
import com.masmatix.suite.core.web.BusinessRuleException;
import com.masmatix.suite.core.web.NotFoundException;
import com.masmatix.suite.generated.microfinance.Loan;
import com.masmatix.suite.generated.microfinance.LoanRepayment;
import com.masmatix.suite.generated.microfinance.LoanRepaymentRepository;
import com.masmatix.suite.generated.microfinance.LoanRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
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

/** Repayment schedule with arrears, and taking a repayment with the late fee worked out. */
@RestController
@RequestMapping("/api/microfinance/desk")
public class MicrofinanceController {

    public record RepayRequest(@NotNull @DecimalMin("0.01") BigDecimal amount) {}

    public record LoanScheduleRow(int number, LocalDate dueOn, BigDecimal amount, BigDecimal paidTowards, String status) {}

    public record LoanStatement(Loan loan, List<LoanScheduleRow> schedule, BigDecimal totalRepayable, BigDecimal paid,
                            BigDecimal dueSoFar, BigDecimal arrears, BigDecimal remaining) {}

    private final LoanRepository loans;
    private final LoanRepaymentRepository repayments;
    private final AuditService audit;
    private final Clock clock;

    public MicrofinanceController(LoanRepository loans, LoanRepaymentRepository repayments, AuditService audit, Clock clock) {
        this.loans = loans;
        this.repayments = repayments;
        this.audit = audit;
        this.clock = clock;
    }

    private Loan loan(Long id) {
        return loans.findById(id).orElseThrow(() -> new NotFoundException("loan " + id + " not found"));
    }

    private LoanStatement statement(Loan l) {
        LocalDate today = LocalDate.now(clock);
        List<LoanRules.Due> due = LoanRules.schedule(l.getStartsOn(), l.getMonths(), l.getMonthlyInstalment());
        BigDecimal paid = repayments.findAll().stream().filter(r -> r.getLoanNumber().equalsIgnoreCase(l.getLoanNumber()))
                .map(LoanRepayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        BigDecimal left = paid;
        List<LoanScheduleRow> rows = new ArrayList<>();
        for (LoanRules.Due d : due) {
            BigDecimal towards = left.min(d.amount()).max(BigDecimal.ZERO);
            left = left.subtract(towards);
            String status = towards.compareTo(d.amount()) == 0 ? "PAID"
                    : d.dueOn().isAfter(today) ? (towards.signum() > 0 ? "PART_PAID" : "UPCOMING") : "OVERDUE";
            rows.add(new LoanScheduleRow(d.number(), d.dueOn(), d.amount(), towards, status));
        }
        BigDecimal total = l.getMonthlyInstalment().multiply(BigDecimal.valueOf(l.getMonths())).setScale(2);
        BigDecimal dueSoFar = LoanRules.dueSoFar(due, today);
        return new LoanStatement(l, rows, total, paid, dueSoFar, LoanRules.arrears(dueSoFar, paid), total.subtract(paid).max(BigDecimal.ZERO));
    }

    @GetMapping("/loans/{id}/statement")
    @Transactional(readOnly = true)
    public LoanStatement get(@PathVariable Long id) {
        return statement(loan(id));
    }

    /**
     * Takes a repayment. If instalments are overdue, the late fee on the arrears is recorded as the penalty.
     * A payment larger than what is left is refused; the loan closes when it is fully repaid.
     */
    @PostMapping("/loans/{id}/repay")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public LoanStatement repay(@PathVariable Long id, @Valid @RequestBody RepayRequest req) {
        Loan l = loan(id);
        if (l.getStatus() != null && !"ACTIVE".equals(l.getStatus())) {
            throw new BusinessRuleException("loan " + l.getLoanNumber() + " is " + l.getStatus());
        }
        LoanStatement before = statement(l);
        if (req.amount().compareTo(before.remaining()) > 0) {
            throw new BusinessRuleException("only " + before.remaining().toPlainString() + " is left on " + l.getLoanNumber());
        }
        LoanRepayment r = new LoanRepayment();
        r.setLoanNumber(l.getLoanNumber());
        r.setPaidOn(LocalDate.now(clock));
        r.setAmount(req.amount().setScale(2, RoundingMode.HALF_UP));
        r.setPenalty(LoanRules.lateFee(before.arrears()));
        repayments.save(r);
        LoanStatement after = statement(l);
        if (after.remaining().signum() == 0) {
            l.setStatus("CLOSED");
        }
        audit.record("REPAY", "loan", l.getId(), l.getLoanNumber() + " " + r.getAmount() + " late fee " + r.getPenalty());
        return after;
    }
}
