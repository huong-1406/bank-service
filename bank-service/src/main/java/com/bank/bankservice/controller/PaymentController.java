package com.bank.bankservice.controller;

import com.bank.bankservice.dto.request.PaymentRequest;
import com.bank.bankservice.dto.response.PaymentResponse;
import com.bank.bankservice.security.SecurityUtil;
import com.bank.bankservice.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // 202 Accepted: thông báo cho khách được gửi sau, bất đồng bộ qua ActiveMQ
    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PaymentResponse pay(@Valid @RequestBody PaymentRequest request) {
        return paymentService.pay(SecurityUtil.currentAccountId(), request);
    }
}
