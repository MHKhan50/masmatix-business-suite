package com.masmatix.suite.gym;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GymMemberRepository extends JpaRepository<GymMember, Long> {

    Optional<GymMember> findByMemberCode(String memberCode);

    List<GymMember> findByExpiresOnBetweenOrderByExpiresOn(LocalDate from, LocalDate to);
}
