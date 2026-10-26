package com.masmatix.suite.library;

import com.masmatix.suite.core.audit.AuditService;
import com.masmatix.suite.core.web.BusinessRuleException;
import com.masmatix.suite.core.web.NotFoundException;
import com.masmatix.suite.generated.library.Book;
import com.masmatix.suite.generated.library.BookLoan;
import com.masmatix.suite.generated.library.BookLoanRepository;
import com.masmatix.suite.generated.library.BookRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Issue and return with copy counts and automatic late fines. */
@RestController
@RequestMapping("/api/library/desk")
public class LibraryController {

    public record IssueRequest(@NotBlank String accessionNumber, @NotBlank String borrower) {}

    public record OverdueLoan(BookLoan loan, String title, BigDecimal fineSoFar) {}

    private final BookRepository books;
    private final BookLoanRepository loans;
    private final AuditService audit;
    private final Clock clock;

    public LibraryController(BookRepository books, BookLoanRepository loans, AuditService audit, Clock clock) {
        this.books = books;
        this.loans = loans;
        this.audit = audit;
        this.clock = clock;
    }

    private long onLoan(String accession) {
        return loans.findAll().stream()
                .filter(l -> l.getReturnedOn() == null && l.getAccessionNumber().equalsIgnoreCase(accession)).count();
    }

    @PostMapping("/issue")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public BookLoan issue(@Valid @RequestBody IssueRequest req) {
        Book book = books.findAll().stream().filter(b -> b.getAccessionNumber().equalsIgnoreCase(req.accessionNumber().trim()))
                .findFirst().orElseThrow(() -> new NotFoundException("book " + req.accessionNumber() + " not found"));
        int copies = book.getCopies() == null ? 0 : book.getCopies();
        if (LibraryRules.available(copies, onLoan(book.getAccessionNumber())) == 0) {
            throw new BusinessRuleException("all " + copies + " copies of " + book.getTitle() + " are on loan");
        }
        LocalDate today = LocalDate.now(clock);
        BookLoan l = new BookLoan();
        l.setAccessionNumber(book.getAccessionNumber());
        l.setBorrower(req.borrower().trim());
        l.setIssuedOn(today);
        l.setDueOn(LibraryRules.dueOn(today));
        BookLoan saved = loans.save(l);
        audit.record("ISSUE", "book loan", saved.getId(), book.getAccessionNumber() + " to " + saved.getBorrower());
        return saved;
    }

    @PostMapping("/loans/{id}/return")
    @Transactional
    public BookLoan giveBack(@PathVariable Long id) {
        BookLoan l = loans.findById(id).orElseThrow(() -> new NotFoundException("loan " + id + " not found"));
        if (l.getReturnedOn() != null) {
            throw new BusinessRuleException("this loan was already returned on " + l.getReturnedOn());
        }
        LocalDate today = LocalDate.now(clock);
        l.setReturnedOn(today);
        l.setFine(LibraryRules.fine(l.getDueOn(), today));
        audit.record("RETURN", "book loan", l.getId(), l.getAccessionNumber() + " fine " + l.getFine());
        return l;
    }

    @GetMapping("/overdue")
    @Transactional(readOnly = true)
    public List<OverdueLoan> overdue() {
        LocalDate today = LocalDate.now(clock);
        return loans.findAll().stream()
                .filter(l -> l.getReturnedOn() == null && today.isAfter(l.getDueOn()))
                .sorted(Comparator.comparing(BookLoan::getDueOn))
                .map(l -> new OverdueLoan(l, books.findAll().stream()
                        .filter(b -> b.getAccessionNumber().equalsIgnoreCase(l.getAccessionNumber()))
                        .map(Book::getTitle).findFirst().orElse("?"), LibraryRules.fine(l.getDueOn(), today)))
                .toList();
    }
}
