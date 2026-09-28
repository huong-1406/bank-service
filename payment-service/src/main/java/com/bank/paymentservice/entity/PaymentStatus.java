package com.bank.paymentservice.entity;

public enum PaymentStatus {
    RECEIVED, // đã nhận yêu cầu từ bank-service
    SENT      // đã gửi message vào ActiveMQ
}
