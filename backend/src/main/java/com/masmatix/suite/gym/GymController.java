package com.masmatix.suite.gym;

import com.masmatix.suite.core.pdf.PdfDocumentBuilder;
import com.masmatix.suite.pos.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
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
@RequestMapping("/api/gym")
public class GymController {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public record CheckInRequest(@NotBlank String memberCode) {}

    public record RenewRequest(@NotNull Long planId, @NotNull PaymentMethod method) {}

    private final GymService service;
    private final GymMemberRepository members;
    private final CheckInRepository checkIns;
    private final FeePaymentRepository payments;
    private final String gymName;
    private final String currency;

    public GymController(GymService service, GymMemberRepository members, CheckInRepository checkIns,
                         FeePaymentRepository payments,
                         @Value("${app.gym.name:Demo Gym (sample)}") String gymName,
                         @Value("${app.currency:GBP}") String currency) {
        this.service = service;
        this.members = members;
        this.checkIns = checkIns;
        this.payments = payments;
        this.gymName = gymName;
        this.currency = currency;
    }

    /** 200 in both cases; "allowed" tells the desk screen (or a door relay) what to do. */
    @PostMapping("/check-in")
    public GymService.CheckInResult checkIn(@Valid @RequestBody CheckInRequest req) {
        return service.checkIn(req.memberCode());
    }

    @GetMapping("/check-ins")
    public List<CheckIn> checkIns(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate day = date != null ? date : LocalDate.now(service.clock());
        var zone = service.clock().getZone();
        return checkIns.findByCheckedAtGreaterThanEqualAndCheckedAtLessThanOrderByCheckedAtDesc(
                day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant());
    }

    /** Members whose membership ends in the next N days (for reminder calls). */
    @GetMapping("/expiring")
    public List<GymMember> expiring(@RequestParam(defaultValue = "7") int days) {
        LocalDate today = LocalDate.now(service.clock());
        return members.findByExpiresOnBetweenOrderByExpiresOn(today, today.plusDays(Math.max(0, Math.min(days, 90))));
    }

    @PostMapping("/members/{id}/renew")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    public FeePayment renew(@PathVariable Long id, @Valid @RequestBody RenewRequest req, Authentication auth) {
        return service.renew(id, req.planId(), req.method(), auth.getName());
    }

    @GetMapping("/members/{id}/payments")
    public List<FeePayment> memberPayments(@PathVariable Long id) {
        return payments.findByMemberIdOrderByIdDesc(id);
    }

    @GetMapping(value = "/payments/{id}/receipt.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> receipt(@PathVariable Long id) {
        FeePayment p = service.payment(id);
        byte[] pdf = new PdfDocumentBuilder(true)
                .title(gymName)
                .line("Membership fee receipt G" + String.format("%05d", p.getId()))
                .line(DMY.format(p.getPaidAt().atZone(service.clock().getZone())) + "   Taken by: " + p.getTakenBy())
                .line("GymMember: " + p.getMemberName())
                .line("Plan: " + p.getPlanName() + " (" + p.getMonths() + " month" + (p.getMonths() == 1 ? "" : "s") + ")")
                .line("Valid until: " + DMY.format(p.getNewExpiry()))
                .total("PAID " + currency, p.getAmount().toPlainString())
                .line("Paid by " + p.getMethod())
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"gym-receipt-" + p.getId() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
