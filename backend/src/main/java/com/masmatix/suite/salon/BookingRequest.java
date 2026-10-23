package com.masmatix.suite.salon;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record BookingRequest(
        @NotNull Long stylistId,
        @NotNull Long treatmentId,
        @NotBlank @Size(max = 120) String clientName,
        @Size(max = 30) @Pattern(regexp = "^[+0-9 ()-]{0,30}$", message = "must be a phone number") String clientPhone,
        @NotNull LocalDateTime startsAt) {}
