package com.masmatix.suite.clinic;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PrescriptionRequest(
        @NotNull Long patientId,
        @Size(max = 500) String notes,
        @Size(max = 300) String allergyOverrideReason,
        @NotEmpty @Size(max = 10) List<@Valid Item> items) {

    public record Item(
            @NotNull Long medicineId,
            @NotBlank @Size(max = 60) String dose,
            @NotBlank @Size(max = 60) String frequency,
            @Min(1) @Max(365) int durationDays,
            @Size(max = 200) String instructions) {}
}
