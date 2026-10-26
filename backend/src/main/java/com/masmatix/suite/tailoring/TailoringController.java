package com.masmatix.suite.tailoring;

import com.masmatix.suite.core.audit.AuditService;
import com.masmatix.suite.core.web.BusinessRuleException;
import com.masmatix.suite.core.web.NotFoundException;
import com.masmatix.suite.core.pdf.PdfDocumentBuilder;
import com.masmatix.suite.generated.tailoring.TailoringCustomer;
import com.masmatix.suite.generated.tailoring.TailoringCustomerRepository;
import com.masmatix.suite.generated.tailoring.TailoringOrder;
import com.masmatix.suite.generated.tailoring.TailoringOrderRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
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

/** Order board by stage, balance due, delivery against full payment, and a printed order slip. */
@RestController
@RequestMapping("/api/tailoring/desk")
public class TailoringController {

    public record DeliverRequest(@NotNull @DecimalMin("0.00") BigDecimal payment) {}

    public record TailoringRow(TailoringOrder order, BigDecimal balanceDue, String nextStage) {}

    private final TailoringOrderRepository orders;
    private final TailoringCustomerRepository customers;
    private final AuditService audit;
    private final String shopName;

    public TailoringController(TailoringOrderRepository orders, TailoringCustomerRepository customers, AuditService audit,
                               @Value("${app.tailoring.name:Demo Tailors (sample)}") String shopName) {
        this.orders = orders;
        this.customers = customers;
        this.audit = audit;
        this.shopName = shopName;
    }

    private TailoringOrder order(Long id) {
        return orders.findById(id).orElseThrow(() -> new NotFoundException("tailoring order " + id + " not found"));
    }

    private static BigDecimal price(TailoringOrder o) {
        return o.getPrice() == null ? BigDecimal.ZERO : o.getPrice();
    }

    private static TailoringRow row(TailoringOrder o) {
        return new TailoringRow(o, TailoringRules.balanceDue(price(o), o.getAdvancePaid()), TailoringRules.next(o.getStatus()));
    }

    @GetMapping("/board")
    @Transactional(readOnly = true)
    public List<TailoringRow> board() {
        return orders.findAll().stream().filter(o -> !"DELIVERED".equals(o.getStatus()))
                .sorted(Comparator.comparing(TailoringOrder::getDeliveryOn, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(TailoringController::row).toList();
    }

    @PostMapping("/orders/{id}/next")
    @Transactional
    public TailoringRow next(@PathVariable Long id) {
        TailoringOrder o = order(id);
        String to = TailoringRules.next(o.getStatus());
        if (to == null) {
            throw new BusinessRuleException("order " + o.getOrderNumber() + " is " + o.getStatus() + "; use deliver");
        }
        o.setStatus(to);
        audit.record("STAGE", "tailoring order", o.getId(), o.getOrderNumber() + " to " + to);
        return row(o);
    }

    /** Hands over a READY order only when the payment clears the balance. */
    @PostMapping("/orders/{id}/deliver")
    @Transactional
    public TailoringRow deliver(@PathVariable Long id, @Valid @RequestBody DeliverRequest req) {
        TailoringOrder o = order(id);
        if (!"READY".equals(o.getStatus())) {
            throw new BusinessRuleException("order " + o.getOrderNumber() + " is " + o.getStatus() + ", not READY");
        }
        BigDecimal due = TailoringRules.balanceDue(price(o), o.getAdvancePaid());
        if (req.payment().compareTo(due) != 0) {
            throw new BusinessRuleException("balance due is " + due.toPlainString() + ", payment was " + req.payment().toPlainString());
        }
        o.setAdvancePaid(price(o));
        o.setStatus("DELIVERED");
        audit.record("DELIVER", "tailoring order", o.getId(), o.getOrderNumber() + " paid " + due);
        return row(o);
    }

    @GetMapping("/orders/{id}/slip.pdf")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> slip(@PathVariable Long id) {
        TailoringOrder o = order(id);
        TailoringCustomer c = customers.findAll().stream().filter(x -> x.getFullName().equalsIgnoreCase(o.getCustomerName()))
                .findFirst().orElse(null);
        PdfDocumentBuilder pdf = new PdfDocumentBuilder(true)
                .title(shopName)
                .line("Order " + o.getOrderNumber() + "   " + o.getGarment())
                .line("Customer: " + o.getCustomerName());
        if (c != null) {
            List<List<String>> rows = new ArrayList<>();
            rows.add(List.of("Chest", cm(c.getChestCm())));
            rows.add(List.of("Waist", cm(c.getWaistCm())));
            rows.add(List.of("Length", cm(c.getLengthCm())));
            pdf.table(List.of("Measurement", "cm"), rows, new float[] {3, 2});
        }
        pdf.line("Trial: " + (o.getTrialOn() == null ? "-" : o.getTrialOn()) + "   Delivery: "
                        + (o.getDeliveryOn() == null ? "-" : o.getDeliveryOn()))
                .total("Price", price(o).toPlainString())
                .total("Advance", (o.getAdvancePaid() == null ? BigDecimal.ZERO : o.getAdvancePaid()).toPlainString())
                .total("Balance", TailoringRules.balanceDue(price(o), o.getAdvancePaid()).toPlainString())
                .line("Please bring this slip when you collect.");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"order-slip-" + o.getId() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF).body(pdf.build());
    }

    private static String cm(BigDecimal v) {
        return v == null ? "-" : v.stripTrailingZeros().toPlainString();
    }
}
