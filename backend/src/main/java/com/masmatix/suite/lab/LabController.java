package com.masmatix.suite.lab;

import com.masmatix.suite.core.audit.AuditService;
import com.masmatix.suite.core.web.BusinessRuleException;
import com.masmatix.suite.core.web.NotFoundException;
import com.masmatix.suite.core.pdf.PdfDocumentBuilder;
import com.masmatix.suite.generated.lab.LabOrder;
import com.masmatix.suite.generated.lab.LabOrderRepository;
import com.masmatix.suite.generated.lab.LabTest;
import com.masmatix.suite.generated.lab.LabTestRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Work list, result entry with a range flag, and a printed report. */
@RestController
@RequestMapping("/api/lab/desk")
public class LabController {

    public record ResultRequest(@NotBlank @Size(max = 2000) String result) {}

    public record LabRow(LabOrder order, String testName, String normalRange, LabRules.Flag flag) {}

    private final LabOrderRepository orders;
    private final LabTestRepository tests;
    private final AuditService audit;
    private final String labName;

    public LabController(LabOrderRepository orders, LabTestRepository tests, AuditService audit,
                         @Value("${app.lab.name:Demo Diagnostic Lab (sample)}") String labName) {
        this.orders = orders;
        this.tests = tests;
        this.audit = audit;
        this.labName = labName;
    }

    private LabOrder order(Long id) {
        return orders.findById(id).orElseThrow(() -> new NotFoundException("lab order " + id + " not found"));
    }

    private LabRow row(LabOrder o) {
        LabTest t = tests.findAll().stream().filter(x -> x.getCode().equalsIgnoreCase(o.getTestCode())).findFirst().orElse(null);
        String range = t == null ? null : t.getNormalRange();
        return new LabRow(o, t == null ? o.getTestCode() : t.getName(), range,
                o.getResult() == null || o.getResult().isBlank() ? null : LabRules.flag(o.getResult(), range));
    }

    /** Orders not reported yet, oldest sample first. */
    @GetMapping("/worklist")
    @Transactional(readOnly = true)
    public List<LabRow> worklist() {
        return orders.findAll().stream().filter(o -> !"REPORTED".equals(o.getStatus()))
                .sorted((a, b) -> a.getCollectedOn().compareTo(b.getCollectedOn())).map(this::row).toList();
    }

    @PostMapping("/orders/{id}/start")
    @Transactional
    public LabRow start(@PathVariable Long id) {
        LabOrder o = order(id);
        if (!LabRules.canMove(o.getStatus(), "IN_PROGRESS")) {
            throw new BusinessRuleException("order " + o.getOrderNumber() + " is " + o.getStatus());
        }
        o.setStatus("IN_PROGRESS");
        audit.record("START", "lab order", o.getId(), o.getOrderNumber());
        return row(o);
    }

    /** Saves the result and marks the order reported. A reported order cannot be changed here. */
    @PostMapping("/orders/{id}/result")
    @Transactional
    public LabRow result(@PathVariable Long id, @Valid @RequestBody ResultRequest req) {
        LabOrder o = order(id);
        if (!LabRules.canMove(o.getStatus(), "REPORTED")) {
            throw new BusinessRuleException("order " + o.getOrderNumber() + " is already reported");
        }
        o.setResult(req.result().trim());
        o.setStatus("REPORTED");
        LabRow r = row(o);
        audit.record("REPORT", "lab order", o.getId(), o.getOrderNumber() + " " + o.getResult() + " " + r.flag());
        return r;
    }

    @GetMapping("/orders/{id}/report.pdf")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> report(@PathVariable Long id) {
        LabOrder o = order(id);
        if (!"REPORTED".equals(o.getStatus())) {
            throw new BusinessRuleException("order " + o.getOrderNumber() + " has no reported result yet");
        }
        LabRow r = row(o);
        byte[] pdf = new PdfDocumentBuilder(false)
                .title(labName)
                .line("Report for order " + o.getOrderNumber() + "   Sample collected " + o.getCollectedOn())
                .line("Patient: " + o.getPatientName())
                .table(List.of("Test", "Result", "Normal range", "Flag"),
                        List.of(List.of(r.testName(), o.getResult(), r.normalRange() == null ? "" : r.normalRange(),
                                r.flag() == LabRules.Flag.NOT_NUMERIC ? "" : r.flag().name())),
                        new float[] {4, 2, 3, 1})
                .line("Sample software with invented data. Not a medical report.")
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"lab-report-" + o.getId() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF).body(pdf);
    }
}
