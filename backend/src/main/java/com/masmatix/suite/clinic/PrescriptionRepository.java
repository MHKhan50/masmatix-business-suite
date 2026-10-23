package com.masmatix.suite.clinic;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    Page<Prescription> findAllByOrderByIdDesc(Pageable page);

    List<Prescription> findByPatientIdOrderByIdDesc(Long patientId);
}
