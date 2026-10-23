package com.masmatix.suite.salon;

import com.masmatix.suite.core.audit.AuditService;
import com.masmatix.suite.core.web.BusinessRuleException;
import com.masmatix.suite.core.web.NotFoundException;
import com.masmatix.suite.generated.salon.Stylist;
import com.masmatix.suite.generated.salon.Treatment;
import com.masmatix.suite.generated.salon.TreatmentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalonService {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    @PersistenceContext
    private EntityManager em;

    private final AppointmentRepository appointments;
    private final TreatmentRepository treatments;
    private final AuditService audit;
    private final Clock clock;

    public SalonService(AppointmentRepository appointments, TreatmentRepository treatments, AuditService audit, Clock clock) {
        this.appointments = appointments;
        this.treatments = treatments;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Appointment book(BookingRequest req, String user) {
        // Row lock on the stylist: two receptionists cannot give away the same slot at once.
        Stylist stylist = em.find(Stylist.class, req.stylistId(), LockModeType.PESSIMISTIC_WRITE);
        if (stylist == null) {
            throw new NotFoundException("stylist " + req.stylistId() + " not found");
        }
        if (Boolean.FALSE.equals(stylist.getActive())) {
            throw new BusinessRuleException(stylist.getFullName() + " is not taking bookings");
        }
        Treatment t = treatments.findById(req.treatmentId())
                .orElseThrow(() -> new NotFoundException("treatment " + req.treatmentId() + " not found"));

        LocalDateTime start = req.startsAt();
        LocalDateTime end = start.plusMinutes(t.getDurationMinutes());
        if (start.isBefore(LocalDateTime.now(clock))) {
            throw new BusinessRuleException("Start time is in the past");
        }
        if (!SlotPlanner.onSlot(start)) {
            throw new BusinessRuleException("Bookings start on the quarter hour (e.g. 10:00, 10:15)");
        }
        if (!SlotPlanner.withinOpeningHours(start, end)) {
            throw new BusinessRuleException("Outside opening hours (" + SlotPlanner.OPENS + " to " + SlotPlanner.CLOSES + ")");
        }
        List<Appointment> clash = appointments.findBooked(stylist.getId(), start, end);
        if (!clash.isEmpty()) {
            Appointment c = clash.get(0);
            throw new BusinessRuleException(stylist.getFullName() + " is already booked from "
                    + HM.format(c.getStartsAt()) + " to " + HM.format(c.getEndsAt()));
        }

        Appointment a = new Appointment();
        a.setStylistId(stylist.getId());
        a.setStylistName(stylist.getFullName());
        a.setTreatmentId(t.getId());
        a.setTreatmentName(t.getName());
        a.setClientName(req.clientName().trim());
        a.setClientPhone(req.clientPhone());
        a.setStartsAt(start);
        a.setEndsAt(end);
        a.setPrice(t.getPrice());
        a.setBookedBy(user);
        Appointment saved = appointments.save(a);
        audit.record("BOOK", "appointment", saved.getId(), stylist.getFullName() + " " + start);
        return saved;
    }

    @Transactional
    public Appointment changeStatus(Long id, AppointmentStatus to) {
        Appointment a = appointments.findById(id).orElseThrow(() -> new NotFoundException("appointment " + id + " not found"));
        if (a.getStatus() != AppointmentStatus.BOOKED) {
            throw new BusinessRuleException("Appointment is already " + a.getStatus().name().toLowerCase());
        }
        a.setStatus(to);
        audit.record(to == AppointmentStatus.CANCELLED ? "CANCEL" : "COMPLETE", "appointment", id, null);
        return a;
    }

    public List<Appointment> day(LocalDate date) {
        return appointments.findByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
                date.atStartOfDay(), date.plusDays(1).atStartOfDay());
    }

    public List<String> freeSlots(LocalDate date, Long stylistId, Long treatmentId) {
        Treatment t = treatments.findById(treatmentId)
                .orElseThrow(() -> new NotFoundException("treatment " + treatmentId + " not found"));
        List<SlotPlanner.Interval> busy = appointments
                .findBooked(stylistId, date.atStartOfDay(), date.plusDays(1).atStartOfDay()).stream()
                .map(a -> new SlotPlanner.Interval(a.getStartsAt(), a.getEndsAt()))
                .toList();
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        return SlotPlanner.freeStarts(date, t.getDurationMinutes(), busy, now).stream()
                .map(LocalTime::toString)
                .toList();
    }
}
