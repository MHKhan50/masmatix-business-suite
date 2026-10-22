package com.masmatix.suite.pos;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Copies name, price and tax at the time of sale so old receipts never change. */
@Entity
@Table(name = "pos_sale_line")
public class SaleLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id")
    private Sale sale;

    private Long productId;
    @Column(length = 14)
    private String barcode;
    @Column(length = 120)
    private String name;
    private int qty;
    @Column(precision = 12, scale = 2)
    private BigDecimal unitPrice;
    @Column(precision = 5, scale = 2)
    private BigDecimal taxRate;
    @Column(precision = 12, scale = 2)
    private BigDecimal lineNet;
    @Column(precision = 12, scale = 2)
    private BigDecimal lineTax;

    protected SaleLine() {}

    public SaleLine(Product p, int qty, PricingCalculator.Line priced) {
        this.productId = p.getId();
        this.barcode = p.getBarcode();
        this.name = p.getName();
        this.qty = qty;
        this.unitPrice = p.getUnitPrice();
        this.taxRate = p.getTaxRate();
        this.lineNet = priced.net();
        this.lineTax = priced.tax();
    }

    void setSale(Sale sale) { this.sale = sale; }
    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public String getBarcode() { return barcode; }
    public String getName() { return name; }
    public int getQty() { return qty; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getTaxRate() { return taxRate; }
    public BigDecimal getLineNet() { return lineNet; }
    public BigDecimal getLineTax() { return lineTax; }
}
