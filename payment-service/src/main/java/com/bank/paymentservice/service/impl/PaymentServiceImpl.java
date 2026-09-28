package com.bank.paymentservice.service.impl;

import com.bank.paymentservice.config.JmsConfig;
import com.bank.paymentservice.dto.PaymentRequest;
import com.bank.paymentservice.entity.Payment;
import com.bank.paymentservice.entity.PaymentStatus;
import com.bank.paymentservice.messaging.PaymentMessage;
import com.bank.paymentservice.repository.PaymentRepository;
import com.bank.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Không kiểm tra số dư hay thẻ — bank-service đã kiểm tra hết. Chỉ lưu và chuyển thư.
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final JmsTemplate jmsTemplate;

    @Override
    @Transactional
    public void receivePayment(PaymentRequest request) {
        // 1. Chống trùng: đã nhận rồi thì thôi, không gửi thư lần 2
        if (paymentRepository.existsByTransactionId(request.paymentId())) {
            log.info("paymentId {} đã nhận trước đó, bỏ qua", request.paymentId());
            return;
        }

        // 2. Lưu yêu cầu
        Payment payment = new Payment();
        payment.setTransactionId(request.paymentId());
        payment.setAccountId(request.accountId());
        payment.setAmount(request.amount());
        payment.setCurrency(request.currency());
        payment.setStatus(PaymentStatus.RECEIVED);
        paymentRepository.save(payment);

        // 3. Bỏ thư vào hộp payment.queue. Gửi lỗi thì transaction rollback, bank-service nhận lỗi
        PaymentMessage message = new PaymentMessage(request.paymentId(), request.accountId(),
                request.amount(), request.currency());
        jmsTemplate.convertAndSend(JmsConfig.PAYMENT_QUEUE, message);

        payment.setStatus(PaymentStatus.SENT);
        log.info("Đã gửi message vào {} cho paymentId: {}", JmsConfig.PAYMENT_QUEUE, request.paymentId());
    }
}
