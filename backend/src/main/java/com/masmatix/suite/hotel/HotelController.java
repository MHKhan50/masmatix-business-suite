package com.masmatix.suite.hotel;

import com.masmatix.suite.core.pdf.PdfDocumentBuilder;
import com.masmatix.suite.generated.hotel.Room;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/hotel")
public class HotelController {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final HotelService service;
    private final BookingRepository bookings;
    private final String hotelName;
    private final String currency;

    public HotelController(HotelService service, BookingRepository bookings,
                           @Value("${app.hotel.name:Demo Hotel (sample)}") String hotelName,
                           @Value("${app.currency:GBP}") String currency) {
        this.service = service;
        this.bookings = bookings;
        this.hotelName = hotelName;
        this.currency = currency;
    }

    @GetMapping("/availability")
    public List<Room> availability(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                   @RequestParam(defaultValue = "1") int guests) {
        return service.available(from, to, guests);
    }

    @GetMapping("/bookings")
    public Page<Booking> list(@RequestParam(defaultValue = "0") int page) {
        return bookings.findAllByOrderByIdDesc(PageRequest.of(Math.max(page, 0), 50));
    }

    @GetMapping("/bookings/{id}")
    public Booking get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    public Booking book(@Valid @RequestBody BookingRequest req, Authentication auth) {
        return service.book(req, auth.getName());
    }

    @PostMapping("/bookings/{id}/check-in")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    public Booking checkIn(@PathVariable Long id) {
        return service.checkIn(id);
    }

    @PostMapping("/bookings/{id}/check-out")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    public Booking checkOut(@PathVariable Long id) {
        return service.checkOut(id);
    }

    @PostMapping("/bookings/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Booking cancel(@PathVariable Long id) {
        return service.cancel(id);
    }

    /** Today's arrivals and guests in house. */
    @GetMapping("/front-desk")
    public HotelService.FrontDesk frontDesk(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.frontDesk(date != null ? date : LocalDate.now(service.clock()));
    }

    @GetMapping(value = "/bookings/{id}/invoice.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> invoice(@PathVariable Long id) {
        Booking b = service.get(id);
        long nights = StayRules.nights(b.getCheckIn(), b.getCheckOut());
        byte[] pdf = new PdfDocumentBuilder(false)
                .title(hotelName)
                .line("Invoice for booking " + b.getBookingNumber())
                .line("Guest: " + b.getGuestName() + "    Guests: " + b.getGuests())
                .line("Room " + b.getRoomNumber() + "    Arrive " + DMY.format(b.getCheckIn()) + "    Depart " + DMY.format(b.getCheckOut()))
                .table(List.of("Item", "Nights", "Rate " + currency, "Amount " + currency),
                        List.of(List.of("Room " + b.getRoomNumber(), String.valueOf(nights),
                                b.getNightlyRate().toPlainString(), b.getTotal().toPlainString())),
                        new float[] {4, 1, 2, 2})
                .total("TOTAL " + currency, b.getTotal().toPlainString())
                .line("Status: " + b.getStatus().name().replace('_', ' ').toLowerCase())
                .line("Sample software with invented data.")
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + b.getBookingNumber() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
