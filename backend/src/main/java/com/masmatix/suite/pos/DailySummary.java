package com.masmatix.suite.pos;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailySummary(LocalDate date, int sales, BigDecimal subtotal, BigDecimal tax,
                           BigDecimal total, BigDecimal cash, BigDecimal card) {}
