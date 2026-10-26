package com.masmatix.suite.housing;

import com.masmatix.suite.core.audit.AuditService;
import com.masmatix.suite.core.web.BusinessRuleException;
import com.masmatix.suite.core.web.NotFoundException;
import com.masmatix.suite.generated.housing.Plot;
import com.masmatix.suite.generated.housing.PlotInstalment;
import com.masmatix.suite.generated.housing.PlotInstalmentRepository;
import com.masmatix.suite.generated.housing.PlotRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Overdue instalments, recording a payment, and plot allotment or transfer (managers only). */
@RestController
@RequestMapping("/api/housing/desk")
public class HousingController {

    public record OwnerRequest(@NotBlank @Size(max = 120) String ownerName) {}

    public record OverdueInstalment(PlotInstalment instalment, long daysLate, String ownerName) {}

    private final PlotRepository plots;
    private final PlotInstalmentRepository instalments;
    private final AuditService audit;
    private final Clock clock;

    public HousingController(PlotRepository plots, PlotInstalmentRepository instalments, AuditService audit, Clock clock) {
        this.plots = plots;
        this.instalments = instalments;
        this.audit = audit;
        this.clock = clock;
    }

    private List<PlotInstalment> overdueFor(String plotNumber, LocalDate today) {
        return instalments.findAll().stream()
                .filter(i -> plotNumber == null || i.getPlotNumber().equalsIgnoreCase(plotNumber))
                .filter(i -> HousingRules.daysLate(i.getDueOn(), i.getPaidOn(), today) > 0)
                .sorted(Comparator.comparing(PlotInstalment::getDueOn)).toList();
    }

    @GetMapping("/overdue")
    @Transactional(readOnly = true)
    public List<OverdueInstalment> overdue() {
        LocalDate today = LocalDate.now(clock);
        return overdueFor(null, today).stream().map(i -> new OverdueInstalment(i, HousingRules.daysLate(i.getDueOn(), null, today),
                plots.findAll().stream().filter(p -> p.getPlotNumber().equalsIgnoreCase(i.getPlotNumber()))
                        .map(Plot::getOwnerName).findFirst().orElse(null))).toList();
    }

    @PostMapping("/instalments/{id}/pay")
    @Transactional
    public PlotInstalment pay(@PathVariable Long id) {
        PlotInstalment i = instalments.findById(id).orElseThrow(() -> new NotFoundException("instalment " + id + " not found"));
        if (i.getPaidOn() != null) {
            throw new BusinessRuleException("already paid on " + i.getPaidOn());
        }
        i.setPaidOn(LocalDate.now(clock));
        audit.record("PAY", "instalment", i.getId(), i.getPlotNumber() + " due " + i.getDueOn() + " " + i.getAmount());
        return i;
    }

    /** First owner of an available plot. */
    @PostMapping("/plots/{id}/allot")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public Plot allot(@PathVariable Long id, @Valid @RequestBody OwnerRequest req) {
        Plot p = plot(id);
        if (!"AVAILABLE".equals(p.getStatus()) && p.getStatus() != null) {
            throw new BusinessRuleException("plot " + p.getPlotNumber() + " is " + p.getStatus() + "; use transfer");
        }
        p.setOwnerName(req.ownerName().trim());
        p.setStatus("ALLOTTED");
        audit.record("ALLOT", "plot", p.getId(), p.getPlotNumber() + " to " + p.getOwnerName());
        return p;
    }

    /** New owner; blocked while the plot has overdue instalments. Old and new owner go to the audit log. */
    @PostMapping("/plots/{id}/transfer")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public Plot transfer(@PathVariable Long id, @Valid @RequestBody OwnerRequest req) {
        Plot p = plot(id);
        String block = HousingRules.transferBlock(p.getStatus(), overdueFor(p.getPlotNumber(), LocalDate.now(clock)).size());
        if (block != null) {
            throw new BusinessRuleException("cannot transfer " + p.getPlotNumber() + ": " + block);
        }
        String old = p.getOwnerName();
        p.setOwnerName(req.ownerName().trim());
        p.setStatus("TRANSFERRED");
        audit.record("TRANSFER", "plot", p.getId(), p.getPlotNumber() + " from " + old + " to " + p.getOwnerName());
        return p;
    }

    private Plot plot(Long id) {
        return plots.findById(id).orElseThrow(() -> new NotFoundException("plot " + id + " not found"));
    }
}
