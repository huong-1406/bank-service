package com.bank.notificationservice.messaging;

import com.bank.notificationservice.config.JmsConfig;
import com.bank.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

// Đầu vào của service (thay cho Controller): nghe payment.queue, có message mới là Spring tự gọi hàm này
@Component
@RequiredArgsConstructor
public class PaymentListener {

    private final NotificationService notificationService;

    @JmsListener(destination = JmsConfig.PAYMENT_QUEUE)
    public void onPaymentMessage(PaymentMessage message) {
        notificationService.notifyPaymentConfirmed(message);
    }
}
