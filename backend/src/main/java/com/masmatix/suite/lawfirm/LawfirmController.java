package com.masmatix.suite.lawfirm;

import com.masmatix.suite.core.audit.AuditService;
import com.masmatix.suite.core.web.BusinessRuleException;
import com.masmatix.suite.core.web.NotFoundException;
import com.masmatix.suite.generated.lawfirm.Hearing;
import com.masmatix.suite.generated.lawfirm.HearingRepository;
import com.masmatix.suite.generated.lawfirm.LegalCase;
import com.masmatix.suite.generated.lawfirm.LegalCaseRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
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

/** Hearing calendar, adding a date to a case, and recording the outcome (adjourned to a new date or decided). */
@RestController
@RequestMapping("/api/lawfirm/desk")
public class LawfirmController {

    public record HearingRequest(@NotNull LocalDate hearingOn, @Size(max = 120) String purpose) {}

    /** Either nextHearingOn (adjourned) or decided = true. */
    public record OutcomeRequest(@NotBlank @Size(max = 2000) String outcome, LocalDate nextHearingOn, Boolean decided) {}

    public record CalendarEntry(Hearing hearing, String title, String court, String clientName) {}

    public record CalendarDay(LocalDate date, List<CalendarEntry> hearings) {}

    private final LegalCaseRepository cases;
    private final HearingRepository hearings;
    private final AuditService audit;
    private final Clock clock;

    public LawfirmController(LegalCaseRepository cases, HearingRepository hearings, AuditService audit, Clock clock) {
        this.cases = cases;
        this.hearings = hearings;
        this.audit = audit;
        this.clock = clock;
    }

    private Optional<LegalCase> byNumber(String caseNumber) {
        return cases.findAll().stream().filter(c -> c.getCaseNumber().equalsIgnoreCase(caseNumber)).findFirst();
    }

    private Hearing add(LegalCase c, LocalDate on, String purpose) {
        boolean same = hearings.findAll().stream()
                .anyMatch(h -> h.getCaseNumber().equalsIgnoreCase(c.getCaseNumber()) && on.equals(h.getHearingOn()));
        String why = HearingRules.refuse(c.getStatus(), on, LocalDate.now(clock), same);
        if (why != null) {
            throw new BusinessRuleException("cannot add hearing to " + c.getCaseNumber() + ": " + why);
        }
        Hearing h = new Hearing();
        h.setCaseNumber(c.getCaseNumber());
        h.setHearingOn(on);
        h.setPurpose(purpose);
        Hearing saved = hearings.save(h);
        audit.record("HEARING", "case", c.getId(), c.getCaseNumber() + " on " + on);
        return saved;
    }

    /** Hearings from today for the next N days (1 to 90), grouped by date. */
    @GetMapping("/calendar")
    @Transactional(readOnly = true)
    public List<CalendarDay> calendar(@RequestParam(defaultValue = "14") int days) {
        int span = Math.max(1, Math.min(90, days));
        LocalDate today = LocalDate.now(clock);
        LocalDate end = today.plusDays(span);
        return hearings.findAll().stream()
                .filter(h -> !h.getHearingOn().isBefore(today) && h.getHearingOn().isBefore(end))
                .sorted(Comparator.comparing(Hearing::getHearingOn).thenComparing(Hearing::getId))
                .map(h -> {
                    LegalCase c = byNumber(h.getCaseNumber()).orElse(null);
                    return new CalendarEntry(h, c == null ? null : c.getTitle(), c == null ? null : c.getCourt(), c == null ? null : c.getClientName());
                })
                .collect(Collectors.groupingBy(e -> e.hearing().getHearingOn(), TreeMap::new, Collectors.toList()))
                .entrySet().stream().map(e -> new CalendarDay(e.getKey(), e.getValue())).toList();
    }

    @PostMapping("/cases/{id}/hearings")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Hearing addHearing(@PathVariable Long id, @Valid @RequestBody HearingRequest req) {
        LegalCase c = cases.findById(id).orElseThrow(() -> new NotFoundException("case " + id + " not found"));
        return add(c, req.hearingOn(), req.purpose());
    }

    @PostMapping("/hearings/{id}/outcome")
    @Transactional
    public LegalCase outcome(@PathVariable Long id, @Valid @RequestBody OutcomeRequest req) {
        Hearing h = hearings.findById(id).orElseThrow(() -> new NotFoundException("hearing " + id + " not found"));
        LegalCase c = byNumber(h.getCaseNumber()).orElseThrow(() -> new NotFoundException("case " + h.getCaseNumber() + " not found"));
        boolean decided = Boolean.TRUE.equals(req.decided());
        if (decided == (req.nextHearingOn() != null)) {
            throw new BusinessRuleException("give either a next hearing date or decided, not both");
        }
        if (h.getHearingOn().isAfter(LocalDate.now(clock))) {
            throw new BusinessRuleException("this hearing is on " + h.getHearingOn() + "; record the outcome on or after that day");
        }
        h.setOutcome(req.outcome().trim());
        if (decided) {
            c.setStatus("DECIDED");
        } else {
            add(c, req.nextHearingOn(), "Adjourned from " + h.getHearingOn());
            c.setStatus("ADJOURNED");
        }
        audit.record("OUTCOME", "case", c.getId(), c.getCaseNumber() + " " + c.getStatus());
        return c;
    }
}
