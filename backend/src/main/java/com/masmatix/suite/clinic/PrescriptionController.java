package com.masmatix.suite.clinic;

import com.masmatix.suite.core.pdf.PdfDocumentBuilder;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clinic/prescriptions")
public class PrescriptionController {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PrescriptionService service;
    private final PrescriptionRepository prescriptions;
    private final Clock clock;
    private final String clinicName;

    public PrescriptionController(PrescriptionService service, PrescriptionRepository prescriptions, Clock clock,
                                  @Value("${app.clinic.name:Demo Clinic (sample)}") String clinicName) {
        this.service = service;
        this.prescriptions = prescriptions;
        this.clock = clock;
        this.clinicName = clinicName;
    }

    @GetMapping
    public List<Prescription> list(@RequestParam(required = false) Long patientId) {
        return patientId != null
                ? prescriptions.findByPatientIdOrderByIdDesc(patientId)
                : prescriptions.findAllByOrderByIdDesc(PageRequest.of(0, 100)).getContent();
    }

    @GetMapping("/{id}")
    public Prescription get(@PathVariable Long id) {
        return service.get(id);
    }

    /** Managers stand in for the prescribing clinician in the demo roles. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Prescription create(@Valid @RequestBody PrescriptionRequest req, Authentication auth) {
        return service.create(req, auth.getName());
    }

    @GetMapping(value = "/{id}/print.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> print(@PathVariable Long id) {
        Prescription p = service.get(id);
        List<List<String>> rows = p.getItems().stream()
                .map(i -> List.of(
                        i.getMedicineName() + (i.getStrength() == null ? "" : " " + i.getStrength()),
                        i.getDose(), i.getFrequency(), i.getDurationDays() + " days",
                        i.getInstructions() == null ? "" : i.getInstructions()))
                .toList();
        String allergies = p.getPatientAllergies() == null || p.getPatientAllergies().isBlank()
                ? "None recorded" : p.getPatientAllergies();
        PdfDocumentBuilder pdf = new PdfDocumentBuilder(false)
                .title(clinicName)
                .line("Prescription " + p.getPrescriptionNumber() + "    Date: " + DMY.format(p.getIssuedAt().atZone(clock.getZone())))
                .line("Patient: " + p.getPatientName()
                        + (p.getPatientDateOfBirth() == null ? "" : "    Date of birth: " + DMY.format(p.getPatientDateOfBirth())))
                .line("Allergies: " + allergies)
                .table(List.of("Medicine", "Dose", "Frequency", "Duration", "Instructions"), rows,
                        new float[] {4, 2, 2, 1.5f, 4});
        if (p.getNotes() != null && !p.getNotes().isBlank()) {
            pdf.line("Notes: " + p.getNotes());
        }
        if (p.getAllergyOverrideReason() != null) {
            pdf.line("Allergy warning overridden: " + p.getAllergyOverrideReason());
        }
        pdf.line(" ")
                .line("Prescriber: " + p.getPrescriber() + "        Signature: ______________________")
                .line("Printed by sample software with invented data. Not valid for dispensing.");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + p.getPrescriptionNumber() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf.build());
    }
}
