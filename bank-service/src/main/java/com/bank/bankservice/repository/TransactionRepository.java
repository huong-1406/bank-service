package com.bank.bankservice.repository;

import com.bank.bankservice.entity.Transaction;
import com.bank.bankservice.entity.enums.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    // Dùng khi xoá thẻ: chỉ chặn nếu còn giao dịch PENDING (idx_txn_card_status)
    boolean existsByCardIdAndStatus(Long cardId, TransactionStatus status);
}
