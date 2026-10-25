package com.masmatix.suite.garment;

import com.masmatix.suite.core.audit.AuditService;
import com.masmatix.suite.core.web.BusinessRuleException;
import com.masmatix.suite.core.web.NotFoundException;
import com.masmatix.suite.generated.garment.GarmentExchange;
import com.masmatix.suite.generated.garment.GarmentExchangeRepository;
import com.masmatix.suite.generated.garment.GarmentItem;
import com.masmatix.suite.pos.PaymentMethod;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GarmentService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    public record Item(String styleCode, int qty) {}

    public record ExchangeResult(GarmentExchange exchange, String returnedName, String newName,
                                 BigDecimal difference, boolean returnedToStock) {}

    @PersistenceContext
    private EntityManager em;

    private final GarmentSaleRepository sales;
    private final GarmentExchangeRepository exchanges;
    private final AuditService audit;
    private final Clock clock;

    public GarmentService(GarmentSaleRepository sales, GarmentExchangeRepository exchanges, AuditService audit, Clock clock) {
        this.sales = sales;
        this.exchanges = exchanges;
        this.audit = audit;
        this.clock = clock;
    }

    private static String code(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }

    public Optional<GarmentItem> find(String styleCode) {
        return em.createQuery("select g from GarmentItem g where g.styleCode = :c", GarmentItem.class)
                .setParameter("c", code(styleCode)).getResultStream().findFirst();
    }

    private GarmentItem lock(String styleCode) {
        return em.createQuery("select g from GarmentItem g where g.styleCode = :c", GarmentItem.class)
                .setParameter("c", code(styleCode)).setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultStream().findFirst()
                .orElseThrow(() -> new NotFoundException("No garment with style code " + code(styleCode)));
    }

    private static int stock(GarmentItem g) {
        return g.getStock() == null ? 0 : g.getStock();
    }

    @Transactional
    public GarmentSale sell(List<Item> items, PaymentMethod method, BigDecimal tendered, String cashier) {
        Map<String, Integer> qtyByCode = new LinkedHashMap<>();
        for (Item i : items) {
            qtyByCode.merge(code(i.styleCode()), i.qty(), Integer::sum);
        }
        GarmentSale sale = new GarmentSale();
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (Map.Entry<String, Integer> e : qtyByCode.entrySet()) {
            GarmentItem g = lock(e.getKey());
            int qty = e.getValue();
            if (stock(g) < qty) {
                throw new BusinessRuleException("Only " + stock(g) + " left of " + g.getName() + " " + g.getSizeLabel());
            }
            g.setStock(stock(g) - qty);
            GarmentSaleLine line = new GarmentSaleLine();
            line.setStyleCode(g.getStyleCode());
            line.setName(g.getName());
            line.setSizeLabel(g.getSizeLabel());
            line.setColour(g.getColour());
            line.setUnitPrice(g.getPrice());
            line.setQty(qty);
            line.setLineTotal(g.getPrice().multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP));
            total = total.add(line.getLineTotal());
            sale.getLines().add(line);
        }
        sale.setTotal(total);
        sale.setPaymentMethod(method);
        if (method == PaymentMethod.CASH) {
            if (tendered == null || tendered.compareTo(total) < 0) {
                throw new BusinessRuleException("Cash tendered is less than the total " + total);
            }
            sale.setAmountTendered(tendered.setScale(2, RoundingMode.HALF_UP));
            sale.setChangeDue(sale.getAmountTendered().subtract(total));
        }
        sale.setCashier(cashier);
        sale.setSoldAt(clock.instant());
        sale.setSoldOn(LocalDate.now(clock));
        GarmentSale saved = sales.save(sale);
        saved.setReceiptNumber("G" + saved.getSoldOn().format(DAY) + "-" + String.format("%05d", saved.getId()));
        audit.record("SALE", "garment sale", saved.getId(), saved.getReceiptNumber() + " total " + total);
        return saved;
    }

    /**
     * Swaps one item from a receipt for another style (e.g. size M for L).
     * The returned item must be on the receipt and not already exchanged, inside the exchange window.
     */
    @Transactional
    public ExchangeResult exchange(String receiptNumber, String returnedCode, String newCode, String reason, String notes) {
        GarmentSale sale = sales.findByReceiptNumber(code(receiptNumber))
                .orElseThrow(() -> new NotFoundException("Receipt " + code(receiptNumber) + " not found"));
        em.lock(sale, LockModeType.PESSIMISTIC_WRITE);
        LocalDate today = LocalDate.now(clock);
        if (!ExchangeRules.withinWindow(sale.getSoldOn(), today)) {
            throw new BusinessRuleException("Exchange window of " + ExchangeRules.WINDOW_DAYS + " days has passed");
        }
        GarmentSaleLine line = sale.getLines().stream()
                .filter(l -> l.getStyleCode().equals(code(returnedCode)) && l.getQty() > l.getExchangedQty())
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException(code(returnedCode) + " is not on this receipt or was already exchanged"));
        GarmentItem returned = lock(returnedCode);
        GarmentItem given = lock(newCode);
        if (stock(given) < 1) {
            throw new BusinessRuleException(given.getName() + " " + given.getSizeLabel() + " is out of stock");
        }
        boolean back = ExchangeRules.returnsToStock(reason);
        if (back) {
            returned.setStock(stock(returned) + 1);
        }
        given.setStock(stock(given) - 1);
        line.setExchangedQty(line.getExchangedQty() + 1);

        BigDecimal diff = ExchangeRules.difference(line.getUnitPrice(), given.getPrice());
        GarmentExchange ex = new GarmentExchange();
        ex.setReceiptNumber(sale.getReceiptNumber());
        ex.setStyleCode(returned.getStyleCode());
        ex.setNewStyleCode(given.getStyleCode());
        ex.setReason(reason);
        ex.setExchangedOn(today);
        ex.setPriceDifference(diff);
        ex.setRefundGiven(diff.signum() < 0);
        ex.setNotes(notes == null || notes.isBlank() ? null : notes.trim());
        GarmentExchange saved = exchanges.save(ex);
        audit.record("EXCHANGE", "garment exchange", saved.getId(),
                sale.getReceiptNumber() + " " + returned.getStyleCode() + " to " + given.getStyleCode() + " diff " + diff);
        return new ExchangeResult(saved, returned.getName(), given.getName(), diff, back);
    }

    @Transactional(readOnly = true)
    public GarmentSale sale(Long id) {
        return sales.findById(id).orElseThrow(() -> new NotFoundException("Sale " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public GarmentSale byReceipt(String receiptNumber) {
        return sales.findByReceiptNumber(code(receiptNumber))
                .orElseThrow(() -> new NotFoundException("Receipt " + code(receiptNumber) + " not found"));
    }

    @Transactional(readOnly = true)
    public List<GarmentItem> lowStock(int max) {
        return em.createQuery("select g from GarmentItem g where coalesce(g.stock, 0) <= :m order by g.stock, g.styleCode",
                GarmentItem.class).setParameter("m", max).getResultList();
    }

    public Clock clock() {
        return clock;
    }
}
