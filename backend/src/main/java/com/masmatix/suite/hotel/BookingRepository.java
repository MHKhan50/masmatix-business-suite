package com.masmatix.suite.hotel;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /** Active bookings that hold any night in [from, to). */
    @Query("select b from Booking b where b.status in :statuses and b.checkIn < :to and b.checkOut > :from")
    List<Booking> findHolding(Collection<BookingStatus> statuses, LocalDate from, LocalDate to);

    /** Written as JPQL: in a derived method name, "CheckIn" would be read as "Check" + the keyword "In". */
    @Query("select b from Booking b where b.checkIn = :day and b.status = :status order by b.roomNumber")
    List<Booking> findArrivals(LocalDate day, BookingStatus status);

    List<Booking> findByStatusOrderByCheckOut(BookingStatus status);

    Page<Booking> findAllByOrderByIdDesc(Pageable page);
}
