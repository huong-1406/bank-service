package com.bank.bankservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

// Nạp tiền không cần thẻ
public record DepositRequest(
        @NotNull(message = "Số tiền không được để trống")
        @DecimalMin(value = "0", inclusive = false, message = "Số tiền phải lớn hơn 0")
        @Digits(integer = 17, fraction = 2, message = "Số tiền tối đa 2 chữ số thập phân")
        BigDecimal amount,

        // Không gửi thì mặc định VND
        @Pattern(regexp = "^[A-Z]{3}$", message = "Mã tiền tệ gồm 3 chữ in hoa, ví dụ VND")
        String currency
) {
}
