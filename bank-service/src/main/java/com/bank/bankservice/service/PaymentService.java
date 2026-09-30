package com.bank.bankservice.service;

import com.bank.bankservice.dto.request.PaymentRequest;
import com.bank.bankservice.dto.response.PaymentResponse;

public interface PaymentService {

    PaymentResponse pay(Long accountId, PaymentRequest request);
}
