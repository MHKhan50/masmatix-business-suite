package com.masmatix.suite.gym;

import com.masmatix.suite.core.crud.BaseEntity;
import com.masmatix.suite.pos.PaymentMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "gym_fee_payment")
public class FeePayment extends BaseEntity {

    @Column(nullable = false)
    private Long memberId;
    @Column(nullable = false, length = 120)
    private String memberName;
    @Column(nullable = false, length = 120)
    private String planName;
    private int months;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentMethod method;
    private LocalDate previousExpiry;
    @Column(nullable = false)
    private LocalDate newExpiry;
    @Column(nullable = false)
    private Instant paidAt;
    @Column(length = 80)
    private String takenBy;

    public Long getMemberId() { return memberId; }
    public void setMemberId(Long v) { this.memberId = v; }
    public String getMemberName() { return memberName; }
    public void setMemberName(String v) { this.memberName = v; }
    public String getPlanName() { return planName; }
    public void setPlanName(String v) { this.planName = v; }
    public int getMonths() { return months; }
    public void setMonths(int v) { this.months = v; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal v) { this.amount = v; }
    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod v) { this.method = v; }
    public LocalDate getPreviousExpiry() { return previousExpiry; }
    public void setPreviousExpiry(LocalDate v) { this.previousExpiry = v; }
    public LocalDate getNewExpiry() { return newExpiry; }
    public void setNewExpiry(LocalDate v) { this.newExpiry = v; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant v) { this.paidAt = v; }
    public String getTakenBy() { return takenBy; }
    public void setTakenBy(String v) { this.takenBy = v; }
}
