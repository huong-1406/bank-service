package com.bank.notificationservice.service.impl;

import com.bank.notificationservice.entity.Notification;
import com.bank.notificationservice.messaging.PaymentMessage;
import com.bank.notificationservice.repository.NotificationRepository;
import com.bank.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Override
    @Transactional
    public void notifyPaymentConfirmed(PaymentMessage message) {
        // 1. Chống trùng: ActiveMQ có thể giao lại message đã xử lý → bỏ qua, không log 2 lần
        if (notificationRepository.existsByPaymentId(message.paymentId())) {
            log.info("paymentId {} đã thông báo trước đó, bỏ qua", message.paymentId());
            return;
        }

        // 2. Lưu thông báo
        String text = "Payment confirmed for paymentId: " + message.paymentId();
        Notification notification = new Notification();
        notification.setPaymentId(message.paymentId());
        notification.setAccountId(message.accountId());
        notification.setAmount(message.amount());
        notification.setCurrency(message.currency());
        notification.setMessage(text);
        notificationRepository.save(notification);

        // 3. Thông báo — đề bài yêu cầu log ra console đúng câu này
        log.info(text);
    }
}
