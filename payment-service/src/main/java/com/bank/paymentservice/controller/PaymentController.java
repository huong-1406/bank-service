package com.bank.paymentservice.controller;

import com.bank.paymentservice.dto.PaymentRequest;
import com.bank.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// API nội bộ — chỉ bank-service gọi, không cần token
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // 202 Accepted: đã nhận và đưa vào hàng đợi, thông báo chưa gửi xong
    @PostMapping
    public ResponseEntity<Void> receivePayment(@Valid @RequestBody PaymentRequest request) {
        paymentService.receivePayment(request);
        return ResponseEntity.accepted().build();
    }
}
