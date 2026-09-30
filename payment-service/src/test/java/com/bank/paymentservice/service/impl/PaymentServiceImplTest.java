package com.bank.paymentservice.service.impl;

import com.bank.paymentservice.config.JmsConfig;
import com.bank.paymentservice.dto.PaymentRequest;
import com.bank.paymentservice.entity.Payment;
import com.bank.paymentservice.entity.PaymentStatus;
import com.bank.paymentservice.messaging.PaymentMessage;
import com.bank.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.core.JmsTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// API nội bộ: POST /payments của payment-service
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private JmsTemplate jmsTemplate;
    @InjectMocks private PaymentServiceImpl paymentService;

    private final PaymentRequest request = new PaymentRequest(44L, 1L, new BigDecimal("300000"), "VND");

    @Test
    @DisplayName("✅ Yêu cầu mới → lưu payment SENT, gửi message vào payment.queue")
    void receivePayment_success() {
        when(paymentRepository.existsByTransactionId(44L)).thenReturn(false);
        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);

        paymentService.receivePayment(request);

        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.SENT);
        verify(jmsTemplate).convertAndSend(JmsConfig.PAYMENT_QUEUE,
                new PaymentMessage(44L, 1L, new BigDecimal("300000"), "VND"));
    }

    @Test
    @DisplayName("❌ Trùng paymentId → bỏ qua, không lưu, không gửi message lần 2")
    void receivePayment_duplicate() {
        when(paymentRepository.existsByTransactionId(44L)).thenReturn(true);

        paymentService.receivePayment(request);

        verify(paymentRepository, never()).save(any());
        verify(jmsTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }
}
