package com.bank.paymentservice.service;

import com.bank.paymentservice.dto.PaymentRequest;

public interface PaymentService {

    void receivePayment(PaymentRequest request);
}
