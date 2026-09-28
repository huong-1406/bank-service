package com.bank.paymentservice.repository;

import com.bank.paymentservice.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // Chống trùng: yêu cầu này đã nhận chưa?
    boolean existsByTransactionId(Long transactionId);
}
