package com.masmatix.suite.pos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record CheckoutRequest(
        @NotEmpty @Valid List<Item> items,
        @NotNull PaymentMethod paymentMethod,
        @DecimalMin("0.00") BigDecimal amountTendered) {

    public record Item(@NotBlank String barcode, @Min(1) int qty) {}
}
