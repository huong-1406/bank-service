package com.bank.bankservice.service.impl;

import com.bank.bankservice.client.PaymentClient;
import com.bank.bankservice.config.RedisConfig;
import com.bank.bankservice.dto.request.PaymentRequest;
import com.bank.bankservice.dto.response.BalanceResponse;
import com.bank.bankservice.dto.response.PaymentResponse;
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
import com.bank.bankservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

// Luồng thanh toán — ARCHITECTURE2.md §7
// Chia 2 lần lưu database riêng: giao dịch PENDING phải lưu chắc TRƯỚC khi gọi sang service khác,
// rồi mới chốt COMPLETED / FAILED. Vì vậy dùng TransactionTemplate thay cho @Transactional.
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final String DEFAULT_CURRENCY = "VND";

    private final BalanceRepository balanceRepository;
    private final CardRepository cardRepository;
    private final TransactionRepository transactionRepository;
    private final PaymentClient paymentClient;
    private final TransactionTemplate transactionTemplate;
    private final CacheManager cacheManager;

    @Override
    public PaymentResponse pay(Long accountId, PaymentRequest request) {
        // Lần lưu 1: kiểm tra + giữ tiền + ghi PENDING
        Transaction pending = transactionTemplate.execute(status -> holdMoney(accountId, request));

        // Gọi payment-service, nằm NGOÀI transaction
        try {
            paymentClient.sendPayment(new PaymentClient.PaymentServiceRequest(
                    pending.getId(), accountId, pending.getAmount(), pending.getCurrency()));
        } catch (RestClientException e) {
            log.warn("Gọi payment-service thất bại cho transactionId {}: {}", pending.getId(), e.getMessage());
            // Lần lưu 2 (lỗi): FAILED + trả tiền lại
            transactionTemplate.executeWithoutResult(status -> finish(pending.getId(), false));
            throw new BusinessException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);
        }

        // Lần lưu 2 (thành công): COMPLETED + tiền ra hẳn
        Transaction completed = transactionTemplate.execute(status -> finish(pending.getId(), true));
        return PaymentResponse.completed(completed);
    }

    // Kiểm tra giống rút tiền, rồi chuyển tiền từ available sang hold
    private Transaction holdMoney(Long accountId, PaymentRequest request) {
        Card card = cardRepository.findByIdAndAccountId(request.cardId(), accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));
        // Đề bài: chỉ thẻ hợp lệ mới được phép hoạt động
        if (!card.isValid()) {
            throw new BusinessException(ErrorCode.CARD_NOT_ACTIVE);
        }

        Balance balance = findBalance(accountId);
        // Đề bài: không trừ tiền nếu số dư khả dụng không đủ
        if (balance.getAvailableBalance().compareTo(request.amount()) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
        }
        balance.setAvailableBalance(balance.getAvailableBalance().subtract(request.amount()));
        balance.setHoldBalance(balance.getHoldBalance().add(request.amount()));

        Transaction transaction = new Transaction();
        transaction.setAccount(balance.getAccount());
        transaction.setCard(card);
        transaction.setAmount(request.amount());
        transaction.setCurrency(request.currency() != null ? request.currency() : DEFAULT_CURRENCY);
        transaction.setType(TransactionType.PAYMENT);
        transaction.setStatus(TransactionStatus.PENDING);
        transactionRepository.save(transaction);

        refreshCache(balance);
        return transaction;
    }

    // success = true: hold − amount (tiền ra hẳn). success = false: hold − amount, available + amount (trả lại)
    private Transaction finish(Long transactionId, boolean success) {
        Transaction transaction = transactionRepository.findById(transactionId).orElseThrow();
        // Đọc lại số dư mới nhất, vì có thể đã có nạp/rút xảy ra trong lúc gọi payment-service
        Balance balance = findBalance(transaction.getAccount().getId());
        BigDecimal amount = transaction.getAmount();

        balance.setHoldBalance(balance.getHoldBalance().subtract(amount));
        if (success) {
            transaction.setStatus(TransactionStatus.COMPLETED);
        } else {
            balance.setAvailableBalance(balance.getAvailableBalance().add(amount));
            transaction.setStatus(TransactionStatus.FAILED);
        }

        refreshCache(balance);
        return transaction;
    }

    // Giống nạp/rút: ghi số dư mới vào cache balance, xoá cache account. Chỉ chạy sau khi commit thành công
    private void refreshCache(Balance balance) {
        Cache balanceCache = cacheManager.getCache(RedisConfig.BALANCE_CACHE);
        if (balanceCache != null) {
            balanceCache.put(balance.getAccountId(), BalanceResponse.from(balance));
        }
        Cache accountCache = cacheManager.getCache(RedisConfig.ACCOUNT_CACHE);
        if (accountCache != null) {
            accountCache.evict(balance.getAccountId());
        }
    }

    private Balance findBalance(Long accountId) {
        return balanceRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
    }
}
