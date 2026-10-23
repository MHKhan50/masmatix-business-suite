package com.masmatix.suite.hotel;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record BookingRequest(
        @NotNull Long roomId,
        @NotBlank @Size(max = 120) String guestName,
        @Size(max = 30) @Pattern(regexp = "^[+0-9 ()-]{0,30}$", message = "must be a phone number") String guestPhone,
        @Email @Size(max = 160) String guestEmail,
        @Min(1) @Max(8) int guests,
        @NotNull LocalDate checkIn,
        @NotNull LocalDate checkOut) {}
