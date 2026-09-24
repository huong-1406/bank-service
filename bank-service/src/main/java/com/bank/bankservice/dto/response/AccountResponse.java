package com.bank.bankservice.dto.response;

import com.bank.bankservice.entity.Account;
import com.bank.bankservice.entity.Balance;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Không bao giờ trả password, kể cả dạng hash
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccountResponse(
        Long accountId,
        String customerName,
        String email,
        String phoneNumber,
        LocalDateTime createdAt,
        BalanceInfo balance
) {
    public record BalanceInfo(BigDecimal availableBalance, BigDecimal holdBalance) {
    }

    // Dùng cho đăng ký: không kèm số dư
    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getCustomerName(), account.getEmail(),
                account.getPhoneNumber(), account.getCreatedAt(), null);
    }

    // Dùng cho xem / sửa tài khoản: kèm số dư
    public static AccountResponse from(Account account, Balance balance) {
        return new AccountResponse(account.getId(), account.getCustomerName(), account.getEmail(),
                account.getPhoneNumber(), account.getCreatedAt(),
                new BalanceInfo(balance.getAvailableBalance(), balance.getHoldBalance()));
    }
}
