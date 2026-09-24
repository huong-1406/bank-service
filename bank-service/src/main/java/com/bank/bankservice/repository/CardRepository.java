package com.bank.bankservice.repository;

import com.bank.bankservice.entity.Card;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CardRepository extends JpaRepository<Card, Long> {

    List<Card> findByAccountId(Long accountId);

    // Lấy thẻ kèm điều kiện thuộc tài khoản, để không đụng được thẻ của người khác
    Optional<Card> findByIdAndAccountId(Long id, Long accountId);

    boolean existsByAccountId(Long accountId);
}
