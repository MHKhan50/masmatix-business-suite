package com.masmatix.suite.garment;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;

/** One style on a garment receipt. Name, size and price are copied so later edits do not change the receipt. */
@Embeddable
public class GarmentSaleLine {

    @Column(nullable = false, length = 30)
    private String styleCode;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(length = 10)
    private String sizeLabel;
    @Column(length = 40)
    private String colour;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;
    private int qty;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;
    private int exchangedQty;

    public String getStyleCode() { return styleCode; }
    public void setStyleCode(String v) { this.styleCode = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getSizeLabel() { return sizeLabel; }
    public void setSizeLabel(String v) { this.sizeLabel = v; }
    public String getColour() { return colour; }
    public void setColour(String v) { this.colour = v; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal v) { this.unitPrice = v; }
    public int getQty() { return qty; }
    public void setQty(int v) { this.qty = v; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal v) { this.lineTotal = v; }
    public int getExchangedQty() { return exchangedQty; }
    public void setExchangedQty(int v) { this.exchangedQty = v; }
}
