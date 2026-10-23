package com.masmatix.suite.gym;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeePaymentRepository extends JpaRepository<FeePayment, Long> {

    List<FeePayment> findByMemberIdOrderByIdDesc(Long memberId);
}
