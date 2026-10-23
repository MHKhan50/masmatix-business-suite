package com.masmatix.suite.clinic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.masmatix.suite.core.crud.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "clinic_prescription_item")
public class PrescriptionItem extends BaseEntity {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prescription_id")
    private Prescription prescription;

    private Long medicineId;
    @Column(nullable = false, length = 120)
    private String medicineName;
    @Column(length = 120)
    private String strength;
    @Column(length = 40)
    private String form;
    @Column(nullable = false, length = 60)
    private String dose;
    @Column(nullable = false, length = 60)
    private String frequency;
    private int durationDays;
    @Column(length = 200)
    private String instructions;

    public Prescription getPrescription() { return prescription; }
    public void setPrescription(Prescription v) { this.prescription = v; }
    public Long getMedicineId() { return medicineId; }
    public void setMedicineId(Long v) { this.medicineId = v; }
    public String getMedicineName() { return medicineName; }
    public void setMedicineName(String v) { this.medicineName = v; }
    public String getStrength() { return strength; }
    public void setStrength(String v) { this.strength = v; }
    public String getForm() { return form; }
    public void setForm(String v) { this.form = v; }
    public String getDose() { return dose; }
    public void setDose(String v) { this.dose = v; }
    public String getFrequency() { return frequency; }
    public void setFrequency(String v) { this.frequency = v; }
    public int getDurationDays() { return durationDays; }
    public void setDurationDays(int v) { this.durationDays = v; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String v) { this.instructions = v; }
}
