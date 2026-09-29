package com.bank.notificationservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Bảng notification trong notification_db — DATABASE.md §13
@Entity
@Table(name = "notification", uniqueConstraints =
        @UniqueConstraint(name = "uk_notification_payment_id", columnNames = "payment_id"))
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // UNIQUE: ActiveMQ giao lại cùng message thì database cũng chặn, không lưu 2 lần
    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    // Câu thông báo đã log ra console
    @Column(nullable = false)
    private String message;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
