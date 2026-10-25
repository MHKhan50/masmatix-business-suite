package com.masmatix.suite.garment;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GarmentSaleRepository extends JpaRepository<GarmentSale, Long> {

    Optional<GarmentSale> findByReceiptNumber(String receiptNumber);
}
