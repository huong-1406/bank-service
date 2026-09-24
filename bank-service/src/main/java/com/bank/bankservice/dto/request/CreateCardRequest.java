package com.bank.bankservice.dto.request;

import com.bank.bankservice.entity.enums.CardType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateCardRequest(
        @NotNull(message = "Loại thẻ không được để trống")
        CardType cardType,

        @NotNull(message = "Ngày hết hạn không được để trống")
        @Future(message = "Ngày hết hạn phải ở tương lai")
        LocalDate expiryDate
) {
}
