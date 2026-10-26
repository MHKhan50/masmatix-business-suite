package com.masmatix.suite.weighbridge;

import com.masmatix.suite.core.audit.AuditService;
import com.masmatix.suite.core.web.BusinessRuleException;
import com.masmatix.suite.core.web.NotFoundException;
import com.masmatix.suite.core.pdf.PdfDocumentBuilder;
import com.masmatix.suite.generated.weighbridge.WeighSlip;
import com.masmatix.suite.generated.weighbridge.WeighSlipRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Two weighings in, net weight and a numbered slip out; daily totals per material. */
@RestController
@RequestMapping("/api/weighbridge/desk")
public class WeighbridgeController {

    public record WeighRequest(@NotBlank @Size(max = 20) String vehicleNumber,
                               @NotBlank @Pattern(regexp = "WHEAT|COTTON|YARN|MAIZE|OTHER") String material,
                               @Size(max = 120) String supplierName,
                               @NotNull Integer grossKg, @NotNull Integer tareKg) {}

    public record WeighResult(WeighSlip slip, int netKg) {}

    public record WeighDay(LocalDate date, int slips, Map<String, Integer> netKgByMaterial, int totalNetKg) {}

    private final WeighSlipRepository slips;
    private final AuditService audit;
    private final Clock clock;
    private final String millName;

    public WeighbridgeController(WeighSlipRepository slips, AuditService audit, Clock clock,
                                 @Value("${app.weighbridge.name:Demo Mill Weighbridge (sample)}") String millName) {
        this.slips = slips;
        this.audit = audit;
        this.clock = clock;
        this.millName = millName;
    }

    @PostMapping("/weigh")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public WeighResult weigh(@Valid @RequestBody WeighRequest req) {
        int net;
        try {
            net = WeighbridgeRules.netKg(req.grossKg(), req.tareKg());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException(e.getMessage());
        }
        String last = slips.findAll().stream().map(WeighSlip::getSlipNumber).filter(n -> n.matches("WB-\\d{6}"))
                .max(Comparator.naturalOrder()).orElse(null);
        WeighSlip s = new WeighSlip();
        s.setSlipNumber(WeighbridgeRules.nextSlip(last));
        s.setVehicleNumber(req.vehicleNumber().trim().toUpperCase());
        s.setMaterial(req.material());
        s.setSupplierName(req.supplierName());
        s.setGrossKg(req.grossKg());
        s.setTareKg(req.tareKg());
        s.setWeighedOn(LocalDate.now(clock));
        WeighSlip saved = slips.save(s);
        audit.record("WEIGH", "weigh slip", saved.getId(), saved.getSlipNumber() + " " + saved.getVehicleNumber() + " net " + net + " kg");
        return new WeighResult(saved, net);
    }

    @GetMapping("/day")
    @Transactional(readOnly = true)
    public WeighDay day(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Map<String, Integer> byMaterial = new TreeMap<>();
        int count = 0;
        int total = 0;
        for (WeighSlip s : slips.findAll()) {
            if (!date.equals(s.getWeighedOn()) || s.getGrossKg() == null || s.getTareKg() == null || s.getGrossKg() <= s.getTareKg()) {
                continue;
            }
            int net = s.getGrossKg() - s.getTareKg();
            byMaterial.merge(s.getMaterial(), net, Integer::sum);
            count++;
            total += net;
        }
        return new WeighDay(date, count, byMaterial, total);
    }

    @GetMapping("/slips/{id}/slip.pdf")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> print(@PathVariable Long id) {
        WeighSlip s = slips.findById(id).orElseThrow(() -> new NotFoundException("weigh slip " + id + " not found"));
        int net;
        try {
            net = WeighbridgeRules.netKg(s.getGrossKg(), s.getTareKg());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("slip " + s.getSlipNumber() + ": " + e.getMessage());
        }
        byte[] pdf = new PdfDocumentBuilder(true)
                .title(millName)
                .line("Weigh slip " + s.getSlipNumber() + "   " + s.getWeighedOn())
                .line("Vehicle " + s.getVehicleNumber() + "   " + s.getMaterial())
                .line("Supplier: " + (s.getSupplierName() == null ? "-" : s.getSupplierName()))
                .total("Gross kg", String.valueOf(s.getGrossKg()))
                .total("Tare kg", String.valueOf(s.getTareKg()))
                .total("NET kg", String.valueOf(net))
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"weigh-slip-" + s.getId() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF).body(pdf);
    }
}
