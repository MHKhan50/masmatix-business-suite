package com.masmatix.suite.clinic;

import com.masmatix.suite.core.audit.AuditService;
import com.masmatix.suite.core.web.BusinessRuleException;
import com.masmatix.suite.core.web.NotFoundException;
import com.masmatix.suite.generated.clinic.Medicine;
import com.masmatix.suite.generated.clinic.MedicineRepository;
import com.masmatix.suite.generated.clinic.Patient;
import com.masmatix.suite.generated.clinic.PatientRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrescriptionService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PrescriptionRepository prescriptions;
    private final PatientRepository patients;
    private final MedicineRepository medicines;
    private final AuditService audit;
    private final Clock clock;

    public PrescriptionService(PrescriptionRepository prescriptions, PatientRepository patients,
                               MedicineRepository medicines, AuditService audit, Clock clock) {
        this.prescriptions = prescriptions;
        this.patients = patients;
        this.medicines = medicines;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Prescription create(PrescriptionRequest req, String prescriber) {
        Patient patient = patients.findById(req.patientId())
                .orElseThrow(() -> new NotFoundException("patient " + req.patientId() + " not found"));
        String override = req.allergyOverrideReason() == null ? "" : req.allergyOverrideReason().trim();

        Prescription p = new Prescription();
        p.setPatientId(patient.getId());
        p.setPatientName(patient.getFullName());
        p.setPatientDateOfBirth(patient.getDateOfBirth());
        p.setPatientAllergies(patient.getAllergies());
        p.setPrescriber(prescriber);
        p.setIssuedAt(clock.instant());
        p.setNotes(req.notes());

        StringBuilder warnings = new StringBuilder();
        for (PrescriptionRequest.Item it : req.items()) {
            Medicine m = medicines.findById(it.medicineId())
                    .orElseThrow(() -> new NotFoundException("medicine " + it.medicineId() + " not found"));
            Optional<String> allergy = AllergyCheck.firstMatch(patient.getAllergies(), m.getName(), m.getIngredient());
            if (allergy.isPresent()) {
                if (override.isEmpty()) {
                    throw new BusinessRuleException("Patient has a recorded allergy to " + allergy.get()
                            + " and " + m.getName() + " matches it. Give an override reason to continue.");
                }
                warnings.append(m.getName()).append(" vs ").append(allergy.get()).append("; ");
            }
            PrescriptionItem item = new PrescriptionItem();
            item.setMedicineId(m.getId());
            item.setMedicineName(m.getName());
            item.setStrength(m.getStrength());
            item.setForm(m.getForm());
            item.setDose(it.dose().trim());
            item.setFrequency(it.frequency().trim());
            item.setDurationDays(it.durationDays());
            item.setInstructions(it.instructions());
            p.addItem(item);
        }
        if (warnings.length() > 0) {
            p.setAllergyOverrideReason(override);
        }

        Prescription saved = prescriptions.save(p);
        saved.setPrescriptionNumber("RX" + LocalDate.now(clock).format(DAY) + "-" + String.format("%05d", saved.getId()));
        audit.record("PRESCRIBE", "prescription", saved.getId(), saved.getItems().size() + " item(s)");
        if (warnings.length() > 0) {
            audit.record("ALLERGY_OVERRIDE", "prescription", saved.getId(), warnings + "reason: " + override);
        }
        return saved;
    }

    public Prescription get(Long id) {
        return prescriptions.findById(id).orElseThrow(() -> new NotFoundException("prescription " + id + " not found"));
    }
}
