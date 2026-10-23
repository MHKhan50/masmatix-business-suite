package com.masmatix.suite.gym;

import com.masmatix.suite.core.audit.AuditService;
import com.masmatix.suite.core.web.NotFoundException;
import com.masmatix.suite.generated.gym.MembershipPlan;
import com.masmatix.suite.generated.gym.MembershipPlanRepository;
import com.masmatix.suite.pos.PaymentMethod;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GymService {

    public record CheckInResult(boolean allowed, String memberCode, String memberName,
                                LocalDate expiresOn, long daysLeft, String reason) {}

    @PersistenceContext
    private EntityManager em;

    private final GymMemberRepository members;
    private final MembershipPlanRepository plans;
    private final CheckInRepository checkIns;
    private final FeePaymentRepository payments;
    private final AuditService audit;
    private final Clock clock;

    public GymService(GymMemberRepository members, MembershipPlanRepository plans, CheckInRepository checkIns,
                      FeePaymentRepository payments, AuditService audit, Clock clock) {
        this.members = members;
        this.plans = plans;
        this.checkIns = checkIns;
        this.payments = payments;
        this.audit = audit;
        this.clock = clock;
    }

    /** Desk or door check: always answers, and always logs the attempt. */
    @Transactional
    public CheckInResult checkIn(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        Optional<GymMember> found = members.findByMemberCode(code);
        CheckIn log = new CheckIn();
        log.setMemberCode(code.length() > 20 ? code.substring(0, 20) : code);
        log.setCheckedAt(clock.instant());
        CheckInResult result;
        if (found.isEmpty()) {
            result = new CheckInResult(false, code, null, null, 0, "Unknown member code");
        } else {
            GymMember m = found.get();
            MembershipRules.Decision d = MembershipRules.entry(m.getExpiresOn(), m.isFrozen(), LocalDate.now(clock));
            result = new CheckInResult(d.allowed(), m.getMemberCode(), m.getFullName(), m.getExpiresOn(), d.daysLeft(), d.reason());
            log.setMemberId(m.getId());
            log.setMemberName(m.getFullName());
        }
        log.setAllowed(result.allowed());
        log.setReason(result.reason());
        checkIns.save(log);
        return result;
    }

    @Transactional
    public FeePayment renew(Long memberId, Long planId, PaymentMethod method, String user) {
        // Lock the member so two desks cannot extend the same membership from the same old date.
        GymMember m = em.find(GymMember.class, memberId, LockModeType.PESSIMISTIC_WRITE);
        if (m == null) {
            throw new NotFoundException("member " + memberId + " not found");
        }
        MembershipPlan plan = plans.findById(planId)
                .orElseThrow(() -> new NotFoundException("plan " + planId + " not found"));
        LocalDate previous = m.getExpiresOn();
        LocalDate next = MembershipRules.renewedExpiry(previous, LocalDate.now(clock), plan.getMonths());

        FeePayment p = new FeePayment();
        p.setMemberId(m.getId());
        p.setMemberName(m.getFullName());
        p.setPlanName(plan.getName());
        p.setMonths(plan.getMonths());
        p.setAmount(plan.getFee());
        p.setMethod(method);
        p.setPreviousExpiry(previous);
        p.setNewExpiry(next);
        p.setPaidAt(clock.instant());
        p.setTakenBy(user);
        m.setExpiresOn(next);
        m.setPlanId(plan.getId());
        FeePayment saved = payments.save(p);
        audit.record("RENEW", "member", m.getId(), plan.getName() + " to " + next);
        return saved;
    }

    public FeePayment payment(Long id) {
        return payments.findById(id).orElseThrow(() -> new NotFoundException("payment " + id + " not found"));
    }

    public Clock clock() {
        return clock;
    }
}
