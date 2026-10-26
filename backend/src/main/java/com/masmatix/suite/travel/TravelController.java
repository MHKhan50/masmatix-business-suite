package com.masmatix.suite.travel;

import com.masmatix.suite.core.web.NotFoundException;
import com.masmatix.suite.generated.travel.TravelBooking;
import com.masmatix.suite.generated.travel.TravelBookingRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Per-booking document checklist and the reminder list for trips in the next two weeks. */
@RestController
@RequestMapping("/api/travel/desk")
public class TravelController {

    public record TravelChecklist(TravelBooking booking, long daysLeft, List<String> missing, boolean ready) {}

    private final TravelBookingRepository bookings;
    private final Clock clock;

    public TravelController(TravelBookingRepository bookings, Clock clock) {
        this.bookings = bookings;
        this.clock = clock;
    }

    private TravelChecklist check(TravelBooking b, LocalDate today) {
        List<String> missing = TravelRules.missing(b.getTripType(), b.getPassportReceived(), b.getVisaStatus(), b.getAmount(), b.getPaid());
        return new TravelChecklist(b, TravelRules.daysLeft(b.getTravelOn(), today), missing, missing.isEmpty());
    }

    @GetMapping("/bookings/{id}/checklist")
    @Transactional(readOnly = true)
    public TravelChecklist checklist(@PathVariable Long id) {
        TravelBooking b = bookings.findById(id).orElseThrow(() -> new NotFoundException("booking " + id + " not found"));
        return check(b, LocalDate.now(clock));
    }

    /** Upcoming trips (not yet travelled), soonest first; "remind" lists only those due within 14 days with something missing. */
    @GetMapping("/upcoming")
    @Transactional(readOnly = true)
    public List<TravelChecklist> upcoming() {
        LocalDate today = LocalDate.now(clock);
        return bookings.findAll().stream().filter(b -> b.getTravelOn() != null && !b.getTravelOn().isBefore(today))
                .sorted(Comparator.comparing(TravelBooking::getTravelOn)).map(b -> check(b, today)).toList();
    }

    @GetMapping("/remind")
    @Transactional(readOnly = true)
    public List<TravelChecklist> remind() {
        LocalDate today = LocalDate.now(clock);
        return upcoming().stream().filter(c -> TravelRules.needsReminder(c.booking().getTravelOn(), today, c.missing())).toList();
    }
}
