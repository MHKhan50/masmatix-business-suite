package com.masmatix.suite.salon;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/salon")
public class SalonController {

    private final SalonService service;

    public SalonController(SalonService service) {
        this.service = service;
    }

    /** Diary for one day, all stylists. */
    @GetMapping("/appointments")
    public List<Appointment> day(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.day(date);
    }

    @PostMapping("/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    public Appointment book(@Valid @RequestBody BookingRequest req, Authentication auth) {
        return service.book(req, auth.getName());
    }

    @PostMapping("/appointments/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    public Appointment cancel(@PathVariable Long id) {
        return service.changeStatus(id, AppointmentStatus.CANCELLED);
    }

    @PostMapping("/appointments/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    public Appointment complete(@PathVariable Long id) {
        return service.changeStatus(id, AppointmentStatus.COMPLETED);
    }

    /** Start times ("HH:mm") where the treatment fits for this stylist. */
    @GetMapping("/availability")
    public List<String> availability(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                     @RequestParam Long stylistId, @RequestParam Long treatmentId) {
        return service.freeSlots(date, stylistId, treatmentId);
    }
}
