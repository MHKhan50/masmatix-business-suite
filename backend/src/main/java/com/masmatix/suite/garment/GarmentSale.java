package com.masmatix.suite.garment;

import com.masmatix.suite.core.crud.BaseEntity;
import com.masmatix.suite.pos.PaymentMethod;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** A garment till sale. Prices include VAT, as on UK clothing tags. */
@Entity
@Table(name = "garment_sale")
public class GarmentSale extends BaseEntity {

    @Column(unique = true, length = 30)
    private String receiptNumber;
    @Column(nullable = false)
    private Instant soldAt;
    @Column(nullable = false)
    private LocalDate soldOn;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentMethod paymentMethod;
    @Column(precision = 12, scale = 2)
    private BigDecimal amountTendered;
    @Column(precision = 12, scale = 2)
    private BigDecimal changeDue;
    @Column(length = 80)
    private String cashier;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "garment_sale_line", joinColumns = @JoinColumn(name = "sale_id"))
    @OrderColumn(name = "line_no")
    private List<GarmentSaleLine> lines = new ArrayList<>();

    public String getReceiptNumber() { return receiptNumber; }
    public void setReceiptNumber(String v) { this.receiptNumber = v; }
    public Instant getSoldAt() { return soldAt; }
    public void setSoldAt(Instant v) { this.soldAt = v; }
    public LocalDate getSoldOn() { return soldOn; }
    public void setSoldOn(LocalDate v) { this.soldOn = v; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal v) { this.total = v; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod v) { this.paymentMethod = v; }
    public BigDecimal getAmountTendered() { return amountTendered; }
    public void setAmountTendered(BigDecimal v) { this.amountTendered = v; }
    public BigDecimal getChangeDue() { return changeDue; }
    public void setChangeDue(BigDecimal v) { this.changeDue = v; }
    public String getCashier() { return cashier; }
    public void setCashier(String v) { this.cashier = v; }
    public List<GarmentSaleLine> getLines() { return lines; }
}
