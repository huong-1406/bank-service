package com.bank.bankservice.dto.response;

import com.bank.bankservice.entity.Balance;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BalanceResponse(
        Long transactionId,
        Long accountId,
        BigDecimal availableBalance,
        BigDecimal holdBalance,
        BigDecimal totalBalance
) {
    // Xem số dư: không có transactionId
    public static BalanceResponse from(Balance balance) {
        return from(balance, null);
    }

    // Nạp / rút: kèm id giao dịch vừa tạo. totalBalance tính ra, không lưu trong database
    public static BalanceResponse from(Balance balance, Long transactionId) {
        return new BalanceResponse(transactionId, balance.getAccountId(), balance.getAvailableBalance(),
                balance.getHoldBalance(), balance.getAvailableBalance().add(balance.getHoldBalance()));
    }
}
