package com.bank.bankservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;

@Entity
@Table(name = "balance")
@Getter
@Setter
@NoArgsConstructor
public class Balance {

    // account_id vừa là PK vừa là FK → ép quan hệ 1-1 ở mức database
    @Id
    @Column(name = "account_id")
    private Long accountId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Account account;

    @Column(name = "available_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Column(name = "hold_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal holdBalance = BigDecimal.ZERO;

    // Optimistic lock: hai lệnh ghi cùng lúc thì lệnh sau thất bại, chống trừ tiền hai lần
    @Version
    @Column(nullable = false)
    private Long version;
}
