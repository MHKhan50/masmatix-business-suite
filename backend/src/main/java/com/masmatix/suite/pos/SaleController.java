package com.masmatix.suite.pos;

import com.masmatix.suite.core.pdf.PdfDocumentBuilder;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pos")
public class SaleController {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final SaleService service;
    private final SaleRepository sales;
    private final String shopName;
    private final String currency;

    public SaleController(SaleService service, SaleRepository sales,
                          @Value("${app.pos.shop-name}") String shopName,
                          @Value("${app.currency}") String currency) {
        this.service = service;
        this.sales = sales;
        this.shopName = shopName;
        this.currency = currency;
    }

    @PostMapping("/sales")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    public Sale checkout(@Valid @RequestBody CheckoutRequest req, Authentication auth) {
        return service.checkout(req, auth.getName());
    }

    @GetMapping("/sales")
    public Page<Sale> list(@RequestParam(defaultValue = "0") int page) {
        return sales.findAllByOrderByIdDesc(PageRequest.of(Math.max(page, 0), 50));
    }

    @GetMapping("/sales/{id}")
    public Sale get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping(value = "/sales/{id}/receipt.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> receipt(@PathVariable Long id) {
        Sale s = service.get(id);
        List<List<String>> rows = s.getLines().stream()
                .map(l -> List.of(l.getName(), String.valueOf(l.getQty()), l.getLineNet().toPlainString()))
                .toList();
        PdfDocumentBuilder pdf = new PdfDocumentBuilder(true)
                .title(shopName)
                .line("Receipt " + s.getSaleNumber())
                .line(WHEN.format(s.getSoldAt().atZone(service.zone())) + "   Cashier: " + s.getCashier())
                .table(List.of("Item", "Qty", currency), rows, new float[] {5, 1, 2})
                .total("Subtotal", s.getSubtotal().toPlainString())
                .total("Tax", s.getTax().toPlainString())
                .total("TOTAL " + currency, s.getTotal().toPlainString())
                .line("Paid by " + s.getPaymentMethod());
        if (s.getChangeDue() != null) {
            pdf.line("Tendered " + s.getAmountTendered().toPlainString() + "   Change " + s.getChangeDue().toPlainString());
        }
        pdf.line("Thank you for shopping with us.");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + s.getSaleNumber() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf.build());
    }

    @GetMapping("/reports/daily")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public DailySummary daily(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.daily(date != null ? date : LocalDate.now(service.zone()));
    }
}
