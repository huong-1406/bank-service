package com.bank.paymentservice.messaging;

import java.math.BigDecimal;

// Nội dung lá thư gửi vào ActiveMQ — đúng 4 trường đề bài yêu cầu.
// notification-service có class cùng hình dạng để đọc lại.
public record PaymentMessage(Long paymentId, Long accountId, BigDecimal amount, String currency) {
}
