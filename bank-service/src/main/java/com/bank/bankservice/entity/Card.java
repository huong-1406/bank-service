package com.bank.bankservice.entity;

import com.bank.bankservice.entity.enums.CardStatus;
import com.bank.bankservice.entity.enums.CardType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "card")
@Getter
@Setter
@NoArgsConstructor
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // RESTRICT: database chặn xoá tài khoản khi còn thẻ
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 20)
    private CardType cardType;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    // Không có API kích hoạt thẻ nên thẻ mới mặc định ACTIVE
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardStatus status = CardStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Thẻ hợp lệ = ACTIVE và chưa hết hạn. Phải kiểm tra mỗi lần dùng vì hạn tự đổi theo ngày
    public boolean isValid() {
        return status == CardStatus.ACTIVE && !expiryDate.isBefore(LocalDate.now());
    }
}
