package com.masmatix.suite.pos;

import com.masmatix.suite.core.crud.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

@Entity
@Table(name = "pos_product")
public class Product extends BaseEntity {

    @NotBlank
    @Column(nullable = false, unique = true, length = 40)
    private String sku;

    /** EAN-13 / UPC-A digits as printed under the barcode. */
    @NotBlank
    @Pattern(regexp = "\\d{8,14}", message = "must be 8 to 14 digits")
    @Column(nullable = false, unique = true, length = 14)
    private String barcode;

    @NotBlank
    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 60)
    private String category;

    /** Price before tax. */
    @NotNull
    @DecimalMin("0.00")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    /** Tax rate in percent, e.g. 20.00 for UK standard VAT, 0.00 for zero-rated food. */
    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate;

    @Min(0)
    private int stockQty;

    @Min(0)
    private int reorderLevel;

    private boolean active = true;

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }
    public int getStockQty() { return stockQty; }
    public void setStockQty(int stockQty) { this.stockQty = stockQty; }
    public int getReorderLevel() { return reorderLevel; }
    public void setReorderLevel(int reorderLevel) { this.reorderLevel = reorderLevel; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
