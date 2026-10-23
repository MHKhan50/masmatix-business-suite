package com.masmatix.suite.hotel;

import com.masmatix.suite.core.audit.AuditService;
import com.masmatix.suite.core.web.BusinessRuleException;
import com.masmatix.suite.core.web.NotFoundException;
import com.masmatix.suite.generated.hotel.Room;
import com.masmatix.suite.generated.hotel.RoomRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HotelService {

    /** Statuses that keep a room occupied for their nights. */
    static final Set<BookingStatus> HOLDING = EnumSet.of(BookingStatus.RESERVED, BookingStatus.CHECKED_IN);

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public record FrontDesk(LocalDate date, List<Booking> arrivals, List<Booking> inHouse) {}

    @PersistenceContext
    private EntityManager em;

    private final BookingRepository bookings;
    private final RoomRepository rooms;
    private final AuditService audit;
    private final Clock clock;

    public HotelService(BookingRepository bookings, RoomRepository rooms, AuditService audit, Clock clock) {
        this.bookings = bookings;
        this.rooms = rooms;
        this.audit = audit;
        this.clock = clock;
    }

    public List<Room> available(LocalDate from, LocalDate to, int guests) {
        if (!to.isAfter(from)) {
            throw new BusinessRuleException("Check-out must be after check-in");
        }
        Set<Long> busy = bookings.findHolding(HOLDING, from, to).stream()
                .map(Booking::getRoomId).collect(Collectors.toSet());
        return rooms.findAll().stream()
                .filter(r -> !Boolean.FALSE.equals(r.getActive()))
                .filter(r -> r.getMaxGuests() != null && r.getMaxGuests() >= guests)
                .filter(r -> !busy.contains(r.getId()))
                .sorted(Comparator.comparing(Room::getNightlyRate).thenComparing(Room::getRoomNumber))
                .toList();
    }

    @Transactional
    public Booking book(BookingRequest req, String user) {
        // Row lock on the room: two receptionists cannot sell the same night twice.
        Room room = em.find(Room.class, req.roomId(), LockModeType.PESSIMISTIC_WRITE);
        if (room == null) {
            throw new NotFoundException("room " + req.roomId() + " not found");
        }
        if (Boolean.FALSE.equals(room.getActive())) {
            throw new BusinessRuleException("Room " + room.getRoomNumber() + " is not in service");
        }
        String dateProblem = StayRules.checkDates(req.checkIn(), req.checkOut(), LocalDate.now(clock));
        if (dateProblem != null) {
            throw new BusinessRuleException(dateProblem);
        }
        if (room.getMaxGuests() == null || req.guests() > room.getMaxGuests()) {
            throw new BusinessRuleException("Room " + room.getRoomNumber() + " sleeps up to " + room.getMaxGuests());
        }
        boolean clash = bookings.findHolding(HOLDING, req.checkIn(), req.checkOut()).stream()
                .anyMatch(b -> b.getRoomId().equals(room.getId()));
        if (clash) {
            throw new BusinessRuleException("Room " + room.getRoomNumber() + " is already booked for some of those nights");
        }

        long nights = StayRules.nights(req.checkIn(), req.checkOut());
        BigDecimal rate = room.getNightlyRate();
        Booking b = new Booking();
        b.setRoomId(room.getId());
        b.setRoomNumber(room.getRoomNumber());
        b.setGuestName(req.guestName().trim());
        b.setGuestPhone(req.guestPhone());
        b.setGuestEmail(req.guestEmail());
        b.setGuests(req.guests());
        b.setCheckIn(req.checkIn());
        b.setCheckOut(req.checkOut());
        b.setNightlyRate(rate);
        b.setTotal(StayRules.total(rate, nights));
        b.setBookedBy(user);
        Booking saved = bookings.save(b);
        saved.setBookingNumber("H" + req.checkIn().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%05d", saved.getId()));
        audit.record("BOOK", "booking", saved.getId(), "room " + room.getRoomNumber() + " " + req.checkIn() + " to " + req.checkOut());
        return saved;
    }

    @Transactional
    public Booking checkIn(Long id) {
        Booking b = get(id);
        LocalDate today = LocalDate.now(clock);
        if (b.getStatus() != BookingStatus.RESERVED) {
            throw new BusinessRuleException("Booking is " + b.getStatus().name().toLowerCase().replace('_', ' '));
        }
        if (today.isBefore(b.getCheckIn())) {
            throw new BusinessRuleException("Check-in opens on " + DMY.format(b.getCheckIn()));
        }
        if (!today.isBefore(b.getCheckOut())) {
            throw new BusinessRuleException("The stay has already ended");
        }
        b.setStatus(BookingStatus.CHECKED_IN);
        b.setCheckedInAt(clock.instant());
        audit.record("CHECK_IN", "booking", id, "room " + b.getRoomNumber());
        return b;
    }

    @Transactional
    public Booking checkOut(Long id) {
        Booking b = get(id);
        if (b.getStatus() != BookingStatus.CHECKED_IN) {
            throw new BusinessRuleException("Only checked-in guests can check out");
        }
        b.setStatus(BookingStatus.CHECKED_OUT);
        b.setCheckedOutAt(clock.instant());
        audit.record("CHECK_OUT", "booking", id, "room " + b.getRoomNumber());
        return b;
    }

    @Transactional
    public Booking cancel(Long id) {
        Booking b = get(id);
        if (b.getStatus() != BookingStatus.RESERVED) {
            throw new BusinessRuleException("Only reservations that have not checked in can be cancelled");
        }
        b.setStatus(BookingStatus.CANCELLED);
        audit.record("CANCEL", "booking", id, null);
        return b;
    }

    public FrontDesk frontDesk(LocalDate date) {
        return new FrontDesk(date,
                bookings.findArrivals(date, BookingStatus.RESERVED),
                bookings.findByStatusOrderByCheckOut(BookingStatus.CHECKED_IN));
    }

    public Booking get(Long id) {
        return bookings.findById(id).orElseThrow(() -> new NotFoundException("booking " + id + " not found"));
    }

    public Clock clock() {
        return clock;
    }
}
