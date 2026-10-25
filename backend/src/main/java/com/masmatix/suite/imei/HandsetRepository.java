package com.masmatix.suite.imei;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HandsetRepository extends JpaRepository<Handset, Long> {

    Optional<Handset> findByImei(String imei);
}
