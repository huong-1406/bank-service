package com.bank.bankservice.repository;

import com.bank.bankservice.entity.Balance;
import org.springframework.data.jpa.repository.JpaRepository;

// Khoá chính của Balance chính là accountId, nên findById(accountId) là đủ
public interface BalanceRepository extends JpaRepository<Balance, Long> {
}
