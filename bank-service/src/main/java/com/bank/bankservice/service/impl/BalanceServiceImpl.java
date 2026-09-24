package com.bank.bankservice.service.impl;

import com.bank.bankservice.dto.request.DepositRequest;
import com.bank.bankservice.dto.request.WithdrawRequest;
import com.bank.bankservice.dto.response.BalanceResponse;
import com.bank.bankservice.entity.Balance;
import com.bank.bankservice.entity.Card;
import com.bank.bankservice.entity.Transaction;
import com.bank.bankservice.entity.enums.TransactionStatus;
import com.bank.bankservice.entity.enums.TransactionType;
import com.bank.bankservice.exception.BusinessException;
import com.bank.bankservice.exception.ErrorCode;
import com.bank.bankservice.repository.BalanceRepository;
import com.bank.bankservice.repository.CardRepository;
import com.bank.bankservice.repository.TransactionRepository;
import com.bank.bankservice.service.BalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class BalanceServiceImpl implements BalanceService {

    private static final String DEFAULT_CURRENCY = "VND";

    private final BalanceRepository balanceRepository;
    private final CardRepository cardRepository;
    private final TransactionRepository transactionRepository;

    @Override
    @Transactional(readOnly = true)
    public BalanceResponse getBalance(Long accountId) {
        return BalanceResponse.from(findBalance(accountId));
    }

    @Override
    @Transactional
    public BalanceResponse deposit(Long accountId, DepositRequest request) {
        Balance balance = findBalance(accountId);
        balance.setAvailableBalance(balance.getAvailableBalance().add(request.amount()));

        // Nạp tiền không cần thẻ: card = null (CHECK chk_txn_card_required cho phép với DEPOSIT)
        Transaction transaction = saveCompletedTransaction(balance, null, request.amount(),
                request.currency(), TransactionType.DEPOSIT);

        // Cột version: nếu có lệnh khác ghi cùng lúc thì commit thất bại → 409 CONCURRENT_UPDATE
        return BalanceResponse.from(balance, transaction.getId());
    }

    @Override
    @Transactional
    public BalanceResponse withdraw(Long accountId, WithdrawRequest request) {
        Card card = cardRepository.findByIdAndAccountId(request.cardId(), accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));
        // Đề bài: chỉ thẻ hợp lệ (ACTIVE và chưa hết hạn) mới được phép hoạt động
        if (!card.isValid()) {
            throw new BusinessException(ErrorCode.CARD_NOT_ACTIVE);
        }

        Balance balance = findBalance(accountId);
        // Đề bài: không được trừ tiền nếu số dư khả dụng không đủ. So với available, không so với tổng
        if (balance.getAvailableBalance().compareTo(request.amount()) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
        }
        balance.setAvailableBalance(balance.getAvailableBalance().subtract(request.amount()));

        Transaction transaction = saveCompletedTransaction(balance, card, request.amount(),
                request.currency(), TransactionType.WITHDRAW);
        return BalanceResponse.from(balance, transaction.getId());
    }

    private Transaction saveCompletedTransaction(Balance balance, Card card, BigDecimal amount,
                                                 String currency, TransactionType type) {
        Transaction transaction = new Transaction();
        transaction.setAccount(balance.getAccount());
        transaction.setCard(card);
        transaction.setAmount(amount);
        transaction.setCurrency(currency != null ? currency : DEFAULT_CURRENCY);
        transaction.setType(type);
        transaction.setStatus(TransactionStatus.COMPLETED);
        return transactionRepository.save(transaction);
    }

    private Balance findBalance(Long accountId) {
        return balanceRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
    }
}
