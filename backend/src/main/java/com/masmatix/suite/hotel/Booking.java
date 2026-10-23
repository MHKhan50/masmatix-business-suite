package com.masmatix.suite.hotel;

import com.masmatix.suite.core.crud.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "hotel_booking")
public class Booking extends BaseEntity {

    @Column(unique = true, length = 30)
    private String bookingNumber;
    @Column(nullable = false)
    private Long roomId;
    @Column(nullable = false, length = 20)
    private String roomNumber;
    @Column(nullable = false, length = 120)
    private String guestName;
    @Column(length = 30)
    private String guestPhone;
    @Column(length = 160)
    private String guestEmail;
    private int guests;
    @Column(nullable = false)
    private LocalDate checkIn;
    @Column(nullable = false)
    private LocalDate checkOut;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal nightlyRate;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private BookingStatus status = BookingStatus.RESERVED;
    private Instant checkedInAt;
    private Instant checkedOutAt;
    @Column(length = 80)
    private String bookedBy;

    public String getBookingNumber() { return bookingNumber; }
    public void setBookingNumber(String v) { this.bookingNumber = v; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long v) { this.roomId = v; }
    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String v) { this.roomNumber = v; }
    public String getGuestName() { return guestName; }
    public void setGuestName(String v) { this.guestName = v; }
    public String getGuestPhone() { return guestPhone; }
    public void setGuestPhone(String v) { this.guestPhone = v; }
    public String getGuestEmail() { return guestEmail; }
    public void setGuestEmail(String v) { this.guestEmail = v; }
    public int getGuests() { return guests; }
    public void setGuests(int v) { this.guests = v; }
    public LocalDate getCheckIn() { return checkIn; }
    public void setCheckIn(LocalDate v) { this.checkIn = v; }
    public LocalDate getCheckOut() { return checkOut; }
    public void setCheckOut(LocalDate v) { this.checkOut = v; }
    public BigDecimal getNightlyRate() { return nightlyRate; }
    public void setNightlyRate(BigDecimal v) { this.nightlyRate = v; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal v) { this.total = v; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus v) { this.status = v; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public void setCheckedInAt(Instant v) { this.checkedInAt = v; }
    public Instant getCheckedOutAt() { return checkedOutAt; }
    public void setCheckedOutAt(Instant v) { this.checkedOutAt = v; }
    public String getBookedBy() { return bookedBy; }
    public void setBookedBy(String v) { this.bookedBy = v; }
}
