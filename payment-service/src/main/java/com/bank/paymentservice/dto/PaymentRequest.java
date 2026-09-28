package com.bank.paymentservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

// Dữ liệu bank-service gửi sang — API.md "API nội bộ"
public record PaymentRequest(
        @NotNull Long paymentId,
        @NotNull Long accountId,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$") String currency
) {
}
