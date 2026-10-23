package com.masmatix.suite.gym;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    List<CheckIn> findByCheckedAtGreaterThanEqualAndCheckedAtLessThanOrderByCheckedAtDesc(Instant from, Instant to);
}
