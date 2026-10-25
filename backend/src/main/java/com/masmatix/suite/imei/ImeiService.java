package com.masmatix.suite.imei;

import com.masmatix.suite.core.audit.AuditService;
import com.masmatix.suite.core.web.BusinessRuleException;
import com.masmatix.suite.core.web.NotFoundException;
import com.masmatix.suite.generated.imei.WarrantyClaim;
import com.masmatix.suite.generated.imei.WarrantyClaimRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImeiService {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<String> OPEN = List.of("RECEIVED", "SENT_TO_SERVICE");

    public record Lookup(String imei, boolean checkDigitValid, Handset handset, LocalDate warrantyEndsOn,
                         boolean inWarranty, long daysLeft, List<WarrantyClaim> claims) {}

    public record Ending(Handset handset, LocalDate warrantyEndsOn, long daysLeft) {}

    @PersistenceContext
    private EntityManager em;

    private final HandsetRepository handsets;
    private final WarrantyClaimRepository claims;
    private final AuditService audit;
    private final Clock clock;

    public ImeiService(HandsetRepository handsets, WarrantyClaimRepository claims, AuditService audit, Clock clock) {
        this.handsets = handsets;
        this.claims = claims;
        this.audit = audit;
        this.clock = clock;
    }

    private static String clean(String raw) {
        return raw == null ? "" : raw.replaceAll("[\\s-]", "");
    }

    @Transactional(readOnly = true)
    public Lookup lookup(String raw) {
        String imei = clean(raw);
        boolean valid = ImeiRules.isValid(imei);
        Handset h = valid ? handsets.findByImei(imei).orElse(null) : null;
        if (h == null) {
            return new Lookup(imei, valid, null, null, false, 0, List.of());
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate end = ImeiRules.warrantyEnd(h.getSoldOn(), h.getWarrantyMonths());
        boolean covered = ImeiRules.inWarranty(h.getSoldOn(), h.getWarrantyMonths(), today);
        return new Lookup(imei, true, h, end, covered, covered ? ChronoUnit.DAYS.between(today, end) : 0, claimsFor(imei));
    }

    private List<WarrantyClaim> claimsFor(String imei) {
        return em.createQuery("select c from WarrantyClaim c where c.imei = :imei order by c.id desc", WarrantyClaim.class)
                .setParameter("imei", imei).getResultList();
    }

    /** A claim is accepted only for a known handset, inside its cover, with no other claim still open. */
    @Transactional
    public WarrantyClaim openClaim(String raw, String fault) {
        String imei = clean(raw);
        if (!ImeiRules.isValid(imei)) {
            throw new BusinessRuleException("IMEI " + imei + " is not valid (check digit or length)");
        }
        Handset found = handsets.findByImei(imei)
                .orElseThrow(() -> new NotFoundException("IMEI " + imei + " was not sold by this shop"));
        // Lock the handset row so two counters cannot open two claims for the same phone at once.
        Handset h = em.find(Handset.class, found.getId(), LockModeType.PESSIMISTIC_WRITE);
        LocalDate today = LocalDate.now(clock);
        if (!ImeiRules.inWarranty(h.getSoldOn(), h.getWarrantyMonths(), today)) {
            LocalDate end = ImeiRules.warrantyEnd(h.getSoldOn(), h.getWarrantyMonths());
            throw new BusinessRuleException(end == null ? "This handset has no warranty"
                    : "Warranty ended on " + DMY.format(end));
        }
        boolean open = claimsFor(imei).stream().anyMatch(c -> OPEN.contains(c.getStatus()));
        if (open) {
            throw new BusinessRuleException("This handset already has an open claim");
        }
        WarrantyClaim c = new WarrantyClaim();
        c.setImei(imei);
        c.setFault(fault.trim());
        c.setReceivedOn(today);
        c.setStatus("RECEIVED");
        WarrantyClaim saved = claims.save(c);
        audit.record("CLAIM", "warranty claim", saved.getId(), h.getBrand() + " " + h.getModel() + " " + imei);
        return saved;
    }

    /** Handsets whose cover ends in the next N days, soonest first (for reminder calls or extended-cover offers). */
    @Transactional(readOnly = true)
    public List<Ending> warrantyEnding(int days) {
        LocalDate today = LocalDate.now(clock);
        LocalDate last = today.plusDays(Math.max(0, Math.min(days, 365)));
        return handsets.findAll().stream()
                .map(h -> new Ending(h, ImeiRules.warrantyEnd(h.getSoldOn(), h.getWarrantyMonths()), 0))
                .filter(e -> e.warrantyEndsOn() != null && !e.warrantyEndsOn().isBefore(today) && !e.warrantyEndsOn().isAfter(last))
                .map(e -> new Ending(e.handset(), e.warrantyEndsOn(), ChronoUnit.DAYS.between(today, e.warrantyEndsOn())))
                .sorted(Comparator.comparing(Ending::warrantyEndsOn))
                .toList();
    }
}
