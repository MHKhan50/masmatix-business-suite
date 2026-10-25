package com.masmatix.suite.garment;

import com.masmatix.suite.core.pdf.PdfDocumentBuilder;
import com.masmatix.suite.core.web.NotFoundException;
import com.masmatix.suite.generated.garment.GarmentItem;
import com.masmatix.suite.pos.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/garment")
public class GarmentController {

    private static final DateTimeFormatter DMY_HM = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public record SaleItem(@NotBlank String styleCode, @Min(1) @Max(99) int qty) {}

    public record SaleRequest(@NotEmpty @Size(max = 50) List<@Valid SaleItem> items, @NotNull PaymentMethod paymentMethod,
                              BigDecimal amountTendered) {}

    public record ExchangeRequest(@NotBlank String receiptNumber, @NotBlank String returnedStyleCode,
                                  @NotBlank String newStyleCode,
                                  @NotBlank @Pattern(regexp = "SIZE|COLOUR|FAULT|OTHER") String reason,
                                  @Size(max = 500) String notes) {}

    private final GarmentService service;
    private final String shopName;
    private final String currency;

    public GarmentController(GarmentService service,
                             @Value("${app.garment.name:Demo Garments (sample)}") String shopName,
                             @Value("${app.currency:GBP}") String currency) {
        this.service = service;
        this.shopName = shopName;
        this.currency = currency;
    }

    /** What the till shows after a style-code scan. */
    @GetMapping("/lookup/{styleCode}")
    public GarmentItem lookup(@PathVariable String styleCode) {
        return service.find(styleCode).orElseThrow(() -> new NotFoundException("No garment with style code " + styleCode));
    }

    @PostMapping("/sales")
    @ResponseStatus(HttpStatus.CREATED)
    public GarmentSale sell(@Valid @RequestBody SaleRequest req, Authentication auth) {
        List<GarmentService.Item> items = req.items().stream().map(i -> new GarmentService.Item(i.styleCode(), i.qty())).toList();
        return service.sell(items, req.paymentMethod(), req.amountTendered(), auth.getName());
    }

    @GetMapping("/sales/by-receipt/{receiptNumber}")
    public GarmentSale byReceipt(@PathVariable String receiptNumber) {
        return service.byReceipt(receiptNumber);
    }

    @PostMapping("/exchange")
    @ResponseStatus(HttpStatus.CREATED)
    public GarmentService.ExchangeResult exchange(@Valid @RequestBody ExchangeRequest req) {
        return service.exchange(req.receiptNumber(), req.returnedStyleCode(), req.newStyleCode(), req.reason(), req.notes());
    }

    @GetMapping("/low-stock")
    public List<GarmentItem> lowStock(@RequestParam(defaultValue = "3") int max) {
        return service.lowStock(Math.max(0, Math.min(max, 100)));
    }

    @GetMapping(value = "/sales/{id}/receipt.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> receipt(@PathVariable Long id) {
        GarmentSale s = service.sale(id);
        List<List<String>> rows = s.getLines().stream()
                .map(l -> List.of(l.getName() + " " + l.getSizeLabel() + " " + (l.getColour() == null ? "" : l.getColour()),
                        String.valueOf(l.getQty()), l.getLineTotal().toPlainString()))
                .toList();
        PdfDocumentBuilder pdf = new PdfDocumentBuilder(true)
                .title(shopName)
                .line("Receipt " + s.getReceiptNumber())
                .line(DMY_HM.format(s.getSoldAt().atZone(service.clock().getZone())) + "   Cashier: " + s.getCashier())
                .table(List.of("Item", "Qty", currency), rows, new float[] {5, 1, 2})
                .total("TOTAL " + currency, s.getTotal().toPlainString())
                .line("Paid by " + s.getPaymentMethod());
        if (s.getChangeDue() != null) {
            pdf.line("Tendered " + s.getAmountTendered().toPlainString() + "   Change " + s.getChangeDue().toPlainString());
        }
        pdf.line("Prices include VAT. Exchange within " + ExchangeRules.WINDOW_DAYS + " days with this receipt.");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"garment-receipt-" + s.getId() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf.build());
    }
}
