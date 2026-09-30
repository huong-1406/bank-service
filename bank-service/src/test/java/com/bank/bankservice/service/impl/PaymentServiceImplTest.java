package com.bank.bankservice.service.impl;

import com.bank.bankservice.client.PaymentClient;
import com.bank.bankservice.dto.request.PaymentRequest;
import com.bank.bankservice.dto.response.PaymentResponse;
import com.bank.bankservice.entity.Account;
import com.bank.bankservice.entity.Balance;
import com.bank.bankservice.entity.Card;
import com.bank.bankservice.entity.Transaction;
import com.bank.bankservice.entity.enums.CardStatus;
import com.bank.bankservice.entity.enums.TransactionStatus;
import com.bank.bankservice.exception.ErrorCode;
import com.bank.bankservice.repository.BalanceRepository;
import com.bank.bankservice.repository.CardRepository;
import com.bank.bankservice.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

// API 12: POST /payments
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock private BalanceRepository balanceRepository;
    @Mock private CardRepository cardRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private PaymentClient paymentClient;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private CacheManager cacheManager;
    @InjectMocks private PaymentServiceImpl paymentService;

    private Balance balance;
    private Transaction saved;

    @BeforeEach
    void setUp() {
        // TransactionTemplate giả: chạy thẳng đoạn code bên trong, không cần database thật
        lenient().when(transactionTemplate.execute(any()))
                .thenAnswer(inv -> ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));
        lenient().doCallRealMethod().when(transactionTemplate).executeWithoutResult(any());

        Account account = new Account();
        account.setId(1L);
        balance = new Balance();
        balance.setAccountId(1L);
        balance.setAccount(account);
        balance.setAvailableBalance(new BigDecimal("1000000"));
        balance.setHoldBalance(BigDecimal.ZERO);

        Card card = new Card();
        card.setId(15L);
        card.setStatus(CardStatus.ACTIVE);
        card.setExpiryDate(LocalDate.now().plusYears(5));

        when(cardRepository.findByIdAndAccountId(15L, 1L)).thenReturn(Optional.of(card));
        when(balanceRepository.findById(1L)).thenReturn(Optional.of(balance));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            saved = inv.getArgument(0);
            saved.setId(44L);
            return saved;
        });
        when(transactionRepository.findById(44L)).thenAnswer(inv -> Optional.of(saved));
    }

    @Test
    @DisplayName("API 12 ✅ payment-service nhận → COMPLETED, tiền ra hẳn")
    void pay_success() {
        PaymentResponse response = paymentService.pay(1L, new PaymentRequest(new BigDecimal("300000"), null, 15L));

        assertThat(response.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(balance.getAvailableBalance()).isEqualByComparingTo("700000");
        assertThat(balance.getHoldBalance()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("API 12 ❌ payment-service không trả lời → 503, FAILED, hoàn tiền đủ")
    void pay_paymentServiceDown() {
        doThrow(new ResourceAccessException("Connection refused")).when(paymentClient).sendPayment(any());

        assertThatThrownBy(() -> paymentService.pay(1L, new PaymentRequest(new BigDecimal("300000"), null, 15L)))
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(balance.getAvailableBalance()).isEqualByComparingTo("1000000");
        assertThat(balance.getHoldBalance()).isEqualByComparingTo("0");
    }
}
