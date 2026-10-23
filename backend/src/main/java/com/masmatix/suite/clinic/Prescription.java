package com.masmatix.suite.clinic;

import com.masmatix.suite.core.crud.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** A prescription with a copy of the patient details as they were when it was written. */
@Entity
@Table(name = "clinic_prescription")
public class Prescription extends BaseEntity {

    @Column(unique = true, length = 30)
    private String prescriptionNumber;
    @Column(nullable = false)
    private Long patientId;
    @Column(nullable = false, length = 120)
    private String patientName;
    private LocalDate patientDateOfBirth;
    @Column(length = 2000)
    private String patientAllergies;
    @Column(nullable = false, length = 80)
    private String prescriber;
    @Column(nullable = false)
    private Instant issuedAt;
    @Column(length = 500)
    private String notes;
    @Column(length = 300)
    private String allergyOverrideReason;

    @OneToMany(mappedBy = "prescription", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<PrescriptionItem> items = new ArrayList<>();

    public void addItem(PrescriptionItem item) {
        item.setPrescription(this);
        items.add(item);
    }

    public String getPrescriptionNumber() { return prescriptionNumber; }
    public void setPrescriptionNumber(String v) { this.prescriptionNumber = v; }
    public Long getPatientId() { return patientId; }
    public void setPatientId(Long v) { this.patientId = v; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String v) { this.patientName = v; }
    public LocalDate getPatientDateOfBirth() { return patientDateOfBirth; }
    public void setPatientDateOfBirth(LocalDate v) { this.patientDateOfBirth = v; }
    public String getPatientAllergies() { return patientAllergies; }
    public void setPatientAllergies(String v) { this.patientAllergies = v; }
    public String getPrescriber() { return prescriber; }
    public void setPrescriber(String v) { this.prescriber = v; }
    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant v) { this.issuedAt = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
    public String getAllergyOverrideReason() { return allergyOverrideReason; }
    public void setAllergyOverrideReason(String v) { this.allergyOverrideReason = v; }
    public List<PrescriptionItem> getItems() { return items; }
}
