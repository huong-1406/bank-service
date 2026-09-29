package com.bank.notificationservice.repository;

import com.bank.notificationservice.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // Chống trùng: message của paymentId này đã xử lý chưa?
    boolean existsByPaymentId(Long paymentId);
}
