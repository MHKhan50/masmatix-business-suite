package com.masmatix.suite.imei;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.masmatix.suite.core.crud.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** A handset sold by the shop, identified by its IMEI. Hand-written so the IMEI check digit is enforced on every save. */
@Entity
@Table(name = "imei_handset")
public class Handset extends BaseEntity {

    @NotBlank
    @Pattern(regexp = "\\d{15}", message = "must be 15 digits")
    @Column(nullable = false, unique = true, length = 15)
    private String imei;

    @NotBlank
    @Size(max = 40)
    @Column(nullable = false, length = 40)
    private String brand;

    @NotBlank
    @Size(max = 60)
    @Column(nullable = false, length = 60)
    private String model;

    private LocalDate soldOn;

    @Min(0)
    @Max(36)
    private Integer warrantyMonths;

    @Size(max = 30)
    @Pattern(regexp = "^[+0-9 ()-]{0,30}$", message = "must be a phone number")
    @Column(length = 30)
    private String customerPhone;

    /** Only checked when the IMEI has 15 digits, so a short IMEI shows one message, not two. */
    @Transient
    @JsonIgnore
    @AssertTrue(message = "IMEI check digit is wrong (mistyped number?)")
    public boolean isCheckDigitValid() {
        return imei == null || !imei.matches("\\d{15}") || ImeiRules.isValid(imei);
    }

    public String getImei() { return imei; }
    public void setImei(String v) { this.imei = v == null ? null : v.replaceAll("[\\s-]", ""); }
    public String getBrand() { return brand; }
    public void setBrand(String v) { this.brand = v; }
    public String getModel() { return model; }
    public void setModel(String v) { this.model = v; }
    public LocalDate getSoldOn() { return soldOn; }
    public void setSoldOn(LocalDate v) { this.soldOn = v; }
    public Integer getWarrantyMonths() { return warrantyMonths; }
    public void setWarrantyMonths(Integer v) { this.warrantyMonths = v; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String v) { this.customerPhone = v; }
}
