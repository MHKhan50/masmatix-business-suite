package com.masmatix.suite.pos;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByBarcode(String barcode);

    /** Row lock so two tills cannot sell the last item twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.barcode = :barcode")
    Optional<Product> lockByBarcode(String barcode);

    @Query("select p from Product p where p.active = true and p.stockQty <= p.reorderLevel order by p.stockQty")
    List<Product> findLowStock();
}
