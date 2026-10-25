package com.masmatix.suite.bakery;

import com.masmatix.suite.core.audit.AuditService;
import com.masmatix.suite.core.web.BusinessRuleException;
import com.masmatix.suite.core.web.NotFoundException;
import com.masmatix.suite.generated.bakery.BakeryItem;
import com.masmatix.suite.generated.bakery.BakeryItemRepository;
import com.masmatix.suite.generated.bakery.ProductionBatch;
import com.masmatix.suite.generated.bakery.ProductionBatchRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BakeryService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    public record SaleResult(String itemName, int quantity, BigDecimal unitPrice, BigDecimal total, List<BakeryRules.Take> fromBatches) {}

    public record ExpiryRow(ProductionBatch batch, BakeryRules.Status status) {}

    @PersistenceContext
    private EntityManager em;

    private final BakeryItemRepository items;
    private final ProductionBatchRepository batches;
    private final AuditService audit;
    private final Clock clock;

    public BakeryService(BakeryItemRepository items, ProductionBatchRepository batches, AuditService audit, Clock clock) {
        this.items = items;
        this.batches = batches;
        this.audit = audit;
        this.clock = clock;
    }

    private BakeryItem item(Long id) {
        return items.findById(id).orElseThrow(() -> new NotFoundException("bakery item " + id + " not found"));
    }

    /** Records a new batch; the expiry date comes from the item's shelf life, not from the person at the oven. */
    @Transactional
    public ProductionBatch bake(Long itemId, int quantity) {
        BakeryItem it = item(itemId);
        LocalDate today = LocalDate.now(clock);
        String prefix = "B-" + today.format(DAY) + "-";
        long n = em.createQuery("select count(b) from ProductionBatch b where b.batchCode like :p", Long.class)
                .setParameter("p", prefix + "%").getSingleResult();
        ProductionBatch b = new ProductionBatch();
        b.setBatchCode(prefix + String.format("%02d", n + 1));
        b.setItemName(it.getName());
        b.setQuantity(quantity);
        b.setBakedOn(today);
        b.setExpiresOn(BakeryRules.expiresOn(today, it.getShelfLifeDays()));
        ProductionBatch saved = batches.save(b);
        audit.record("BAKE", "batch", saved.getId(), saved.getBatchCode() + " " + quantity + " x " + it.getName());
        return saved;
    }

    /** Counter sale: takes stock from the batches that expire first and never sells an expired batch. */
    @Transactional
    public SaleResult sell(Long itemId, int quantity) {
        BakeryItem it = item(itemId);
        // Lock the item's batches so two counters cannot sell the same loaves.
        List<ProductionBatch> stock = em.createQuery(
                        "select b from ProductionBatch b where b.itemName = :n and b.quantity > 0 order by b.expiresOn, b.id",
                        ProductionBatch.class)
                .setParameter("n", it.getName()).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        List<BakeryRules.Stock> view = stock.stream()
                .map(b -> new BakeryRules.Stock(b.getId(), b.getBatchCode(), b.getExpiresOn(), b.getQuantity())).toList();
        List<BakeryRules.Take> takes;
        try {
            takes = BakeryRules.allocate(view, quantity, LocalDate.now(clock));
        } catch (IllegalStateException e) {
            throw new BusinessRuleException(e.getMessage() + " of " + it.getName());
        }
        Map<Long, ProductionBatch> byId = stock.stream().collect(Collectors.toMap(ProductionBatch::getId, Function.identity()));
        for (BakeryRules.Take t : takes) {
            ProductionBatch b = byId.get(t.batchId());
            b.setQuantity(b.getQuantity() - t.quantity());
        }
        BigDecimal total = it.getPrice().multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        audit.record("SALE", "bakery item", it.getId(), quantity + " x " + it.getName() + " total " + total);
        return new SaleResult(it.getName(), quantity, it.getPrice(), total, takes);
    }

    /** Batches with stock that are expired or end today or tomorrow, oldest first. */
    @Transactional(readOnly = true)
    public List<ExpiryRow> expiryList() {
        LocalDate today = LocalDate.now(clock);
        return em.createQuery("select b from ProductionBatch b where b.quantity > 0 and b.expiresOn <= :d order by b.expiresOn, b.id",
                        ProductionBatch.class)
                .setParameter("d", today.plusDays(1)).getResultList().stream()
                .map(b -> new ExpiryRow(b, BakeryRules.status(b.getExpiresOn(), today))).toList();
    }

    /** Removes what is left of a batch from sale (expired or damaged) and logs who did it and how many. */
    @Transactional
    public ProductionBatch writeOff(Long batchId) {
        ProductionBatch b = em.find(ProductionBatch.class, batchId, LockModeType.PESSIMISTIC_WRITE);
        if (b == null) {
            throw new NotFoundException("batch " + batchId + " not found");
        }
        int left = b.getQuantity();
        b.setQuantity(0);
        audit.record("WRITE_OFF", "batch", b.getId(), b.getBatchCode() + " " + left + " x " + b.getItemName());
        return b;
    }
}
