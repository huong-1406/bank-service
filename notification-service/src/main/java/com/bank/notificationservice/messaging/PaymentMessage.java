package com.bank.notificationservice.messaging;

import java.math.BigDecimal;

// Nội dung message nhận từ payment.queue — cùng 4 trường với PaymentMessage bên payment-service
public record PaymentMessage(Long paymentId, Long accountId, BigDecimal amount, String currency) {
}
