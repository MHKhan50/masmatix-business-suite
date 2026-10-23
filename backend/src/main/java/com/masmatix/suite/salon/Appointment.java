package com.masmatix.suite.salon;

import com.masmatix.suite.core.crud.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One booking in the diary. Times are local wall-clock times in the salon's time zone. */
@Entity
@Table(name = "salon_appointment")
public class Appointment extends BaseEntity {

    @Column(nullable = false)
    private Long stylistId;
    @Column(nullable = false, length = 120)
    private String stylistName;
    @Column(nullable = false)
    private Long treatmentId;
    @Column(nullable = false, length = 120)
    private String treatmentName;
    @Column(nullable = false, length = 120)
    private String clientName;
    @Column(length = 30)
    private String clientPhone;
    @Column(nullable = false)
    private LocalDateTime startsAt;
    @Column(nullable = false)
    private LocalDateTime endsAt;
    @Column(precision = 12, scale = 2)
    private BigDecimal price;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private AppointmentStatus status = AppointmentStatus.BOOKED;
    @Column(length = 80)
    private String bookedBy;

    public Long getStylistId() { return stylistId; }
    public void setStylistId(Long v) { this.stylistId = v; }
    public String getStylistName() { return stylistName; }
    public void setStylistName(String v) { this.stylistName = v; }
    public Long getTreatmentId() { return treatmentId; }
    public void setTreatmentId(Long v) { this.treatmentId = v; }
    public String getTreatmentName() { return treatmentName; }
    public void setTreatmentName(String v) { this.treatmentName = v; }
    public String getClientName() { return clientName; }
    public void setClientName(String v) { this.clientName = v; }
    public String getClientPhone() { return clientPhone; }
    public void setClientPhone(String v) { this.clientPhone = v; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(LocalDateTime v) { this.startsAt = v; }
    public LocalDateTime getEndsAt() { return endsAt; }
    public void setEndsAt(LocalDateTime v) { this.endsAt = v; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal v) { this.price = v; }
    public AppointmentStatus getStatus() { return status; }
    public void setStatus(AppointmentStatus v) { this.status = v; }
    public String getBookedBy() { return bookedBy; }
    public void setBookedBy(String v) { this.bookedBy = v; }
}
