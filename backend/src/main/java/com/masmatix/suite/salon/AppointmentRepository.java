package com.masmatix.suite.salon;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /** Booked appointments of one stylist that overlap [from, to). */
    @Query("select a from Appointment a where a.stylistId = :stylistId and a.status = com.masmatix.suite.salon.AppointmentStatus.BOOKED"
            + " and a.startsAt < :to and a.endsAt > :from order by a.startsAt")
    List<Appointment> findBooked(Long stylistId, LocalDateTime from, LocalDateTime to);

    List<Appointment> findByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(LocalDateTime from, LocalDateTime to);
}
