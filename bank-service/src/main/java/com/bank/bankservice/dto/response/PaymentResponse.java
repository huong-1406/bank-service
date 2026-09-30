package com.bank.bankservice.dto.response;

import com.bank.bankservice.entity.Transaction;
import com.bank.bankservice.entity.enums.TransactionStatus;

import java.math.BigDecimal;

public record PaymentResponse(
        Long transactionId,
        TransactionStatus status,
        BigDecimal amount,
        String currency,
        String message
) {
    public static PaymentResponse completed(Transaction transaction) {
        return new PaymentResponse(transaction.getId(), transaction.getStatus(), transaction.getAmount(),
                transaction.getCurrency(), "Thanh toán thành công");
    }
}
