package com.bank.notificationservice.service;

import com.bank.notificationservice.messaging.PaymentMessage;

public interface NotificationService {

    void notifyPaymentConfirmed(PaymentMessage message);
}
