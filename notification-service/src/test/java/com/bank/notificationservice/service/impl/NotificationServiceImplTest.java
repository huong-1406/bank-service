package com.bank.notificationservice.service.impl;

import com.bank.notificationservice.entity.Notification;
import com.bank.notificationservice.messaging.PaymentMessage;
import com.bank.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Xử lý message nhận từ payment.queue
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationRepository notificationRepository;
    @InjectMocks private NotificationServiceImpl notificationService;

    private final PaymentMessage message = new PaymentMessage(44L, 1L, new BigDecimal("300000"), "VND");

    @Test
    @DisplayName("✅ Message mới → lưu thông báo \"Payment confirmed for paymentId: 44\"")
    void notifyPaymentConfirmed_success() {
        when(notificationRepository.existsByPaymentId(44L)).thenReturn(false);
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);

        notificationService.notifyPaymentConfirmed(message);

        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getPaymentId()).isEqualTo(44L);
        assertThat(saved.getValue().getMessage()).isEqualTo("Payment confirmed for paymentId: 44");
    }

    @Test
    @DisplayName("❌ Message trùng paymentId → bỏ qua, không lưu lần 2")
    void notifyPaymentConfirmed_duplicate() {
        when(notificationRepository.existsByPaymentId(44L)).thenReturn(true);

        notificationService.notifyPaymentConfirmed(message);

        verify(notificationRepository, never()).save(any());
    }
}
