package com.masmatix.suite.pos;

import com.masmatix.suite.core.audit.AuditService;
import com.masmatix.suite.core.web.BusinessRuleException;
import com.masmatix.suite.core.web.NotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaleService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ProductRepository products;
    private final SaleRepository sales;
    private final AuditService audit;
    private final ZoneId zone;

    public SaleService(ProductRepository products, SaleRepository sales, AuditService audit,
                       @Value("${app.timezone:Europe/London}") String timezone) {
        this.products = products;
        this.sales = sales;
        this.audit = audit;
        this.zone = ZoneId.of(timezone);
    }

    @Transactional
    public Sale checkout(CheckoutRequest req, String cashier) {
        // Merge repeated scans of the same barcode into one line.
        Map<String, Integer> qtyByBarcode = new LinkedHashMap<>();
        for (CheckoutRequest.Item item : req.items()) {
            qtyByBarcode.merge(item.barcode().trim(), item.qty(), Integer::sum);
        }

        Sale sale = new Sale();
        List<PricingCalculator.Line> priced = new ArrayList<>();
        for (Map.Entry<String, Integer> e : qtyByBarcode.entrySet()) {
            Product p = products.lockByBarcode(e.getKey())
                    .filter(Product::isActive)
                    .orElseThrow(() -> new NotFoundException("No active product with barcode " + e.getKey()));
            int qty = e.getValue();
            if (p.getStockQty() < qty) {
                throw new BusinessRuleException("Only " + p.getStockQty() + " left of " + p.getName());
            }
            p.setStockQty(p.getStockQty() - qty);
            PricingCalculator.Line line = PricingCalculator.line(p.getUnitPrice(), p.getTaxRate(), qty);
            priced.add(line);
            sale.addLine(new SaleLine(p, qty, line));
        }

        PricingCalculator.Totals t = PricingCalculator.totals(priced);
        sale.setSubtotal(t.subtotal());
        sale.setTax(t.tax());
        sale.setTotal(t.total());
        sale.setPaymentMethod(req.paymentMethod());
        sale.setCashier(cashier);
        sale.setSoldAt(Instant.now());

        if (req.paymentMethod() == PaymentMethod.CASH) {
            BigDecimal tendered = req.amountTendered();
            if (tendered == null || tendered.compareTo(t.total()) < 0) {
                throw new BusinessRuleException("Cash tendered is less than the total " + t.total());
            }
            sale.setAmountTendered(tendered.setScale(2, java.math.RoundingMode.HALF_UP));
            sale.setChangeDue(sale.getAmountTendered().subtract(t.total()));
        }

        Sale saved = sales.save(sale);
        saved.setSaleNumber("S" + LocalDate.now(zone).format(DAY) + "-" + String.format("%05d", saved.getId()));
        audit.record("SALE", "sale", saved.getId(), saved.getSaleNumber() + " total " + saved.getTotal());
        return saved;
    }

    @Transactional(readOnly = true)
    public Sale get(Long id) {
        Sale s = sales.findById(id).orElseThrow(() -> new NotFoundException("Sale " + id + " not found"));
        s.getLines().size(); // load lines inside the transaction
        return s;
    }

    @Transactional(readOnly = true)
    public DailySummary daily(LocalDate date) {
        Instant from = date.atStartOfDay(zone).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zone).toInstant();
        BigDecimal sub = BigDecimal.ZERO, tax = BigDecimal.ZERO, cash = BigDecimal.ZERO, card = BigDecimal.ZERO;
        List<Sale> day = sales.findBetween(from, to);
        for (Sale s : day) {
            sub = sub.add(s.getSubtotal());
            tax = tax.add(s.getTax());
            if (s.getPaymentMethod() == PaymentMethod.CASH) {
                cash = cash.add(s.getTotal());
            } else {
                card = card.add(s.getTotal());
            }
        }
        return new DailySummary(date, day.size(), sub.setScale(2), tax.setScale(2),
                sub.add(tax).setScale(2), cash.setScale(2), card.setScale(2));
    }

    public ZoneId zone() {
        return zone;
    }
}
