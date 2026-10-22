package com.masmatix.suite.pos;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    Page<Sale> findAllByOrderByIdDesc(Pageable pageable);

    @Query("select s from Sale s where s.soldAt >= :from and s.soldAt < :to")
    List<Sale> findBetween(Instant from, Instant to);
}
