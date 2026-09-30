package com.bank.bankservice.service.impl;

import com.bank.bankservice.dto.request.DepositRequest;
import com.bank.bankservice.dto.request.WithdrawRequest;
import com.bank.bankservice.dto.response.BalanceResponse;
import com.bank.bankservice.entity.Account;
import com.bank.bankservice.entity.Balance;
import com.bank.bankservice.entity.Card;
import com.bank.bankservice.entity.Transaction;
import com.bank.bankservice.entity.enums.CardStatus;
import com.bank.bankservice.exception.ErrorCode;
import com.bank.bankservice.repository.BalanceRepository;
import com.bank.bankservice.repository.CardRepository;
import com.bank.bankservice.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// API 9, 10, 11: xem số dư, nạp tiền, rút tiền
@ExtendWith(MockitoExtension.class)
class BalanceServiceImplTest {

    @Mock private BalanceRepository balanceRepository;
    @Mock private CardRepository cardRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private CacheManager cacheManager;   // không cấu hình → getCache trả null, service bỏ qua cache
    @InjectMocks private BalanceServiceImpl balanceService;

    private Balance balance(String available) {
        Account account = new Account();
        account.setId(1L);
        Balance balance = new Balance();
        balance.setAccountId(1L);
        balance.setAccount(account);
        balance.setAvailableBalance(new BigDecimal(available));
        balance.setHoldBalance(BigDecimal.ZERO);
        return balance;
    }

    private Card card(CardStatus status, LocalDate expiry) {
        Card card = new Card();
        card.setId(15L);
        card.setStatus(status);
        card.setExpiryDate(expiry);
        return card;
    }

    private void transactionGetsId() {
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            t.setId(100L);
            return t;
        });
    }

    // ===== API 9: xem số dư =====
    @Test
    @DisplayName("API 9 ✅ Xem số dư: total = available + hold")
    void getBalance_success() {
        when(balanceRepository.findById(1L)).thenReturn(Optional.of(balance("1000000")));

        BalanceResponse response = balanceService.getBalance(1L);

        assertThat(response.availableBalance()).isEqualByComparingTo("1000000");
        assertThat(response.totalBalance()).isEqualByComparingTo("1000000");
    }

    @Test
    @DisplayName("API 9 ❌ Tài khoản không tồn tại → ACCOUNT_NOT_FOUND")
    void getBalance_notFound() {
        when(balanceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> balanceService.getBalance(99L))
                .extracting("errorCode").isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
    }

    // ===== API 10: nạp tiền =====
    @Test
    @DisplayName("API 10 ✅ Nạp 500.000 → số dư tăng, có giao dịch")
    void deposit_success() {
        Balance balance = balance("1000000");
        when(balanceRepository.findById(1L)).thenReturn(Optional.of(balance));
        transactionGetsId();

        BalanceResponse response = balanceService.deposit(1L, new DepositRequest(new BigDecimal("500000"), null));

        assertThat(response.availableBalance()).isEqualByComparingTo("1500000");
        assertThat(response.transactionId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("API 10 ❌ Tài khoản không tồn tại → ACCOUNT_NOT_FOUND, không ghi giao dịch")
    void deposit_accountNotFound() {
        when(balanceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> balanceService.deposit(99L, new DepositRequest(new BigDecimal("500000"), null)))
                .extracting("errorCode").isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
        verify(transactionRepository, never()).save(any());
    }

    // ===== API 11: rút tiền =====
    @Test
    @DisplayName("API 11 ✅ Rút 200.000 bằng thẻ hợp lệ → số dư giảm")
    void withdraw_success() {
        Balance balance = balance("1000000");
        when(cardRepository.findByIdAndAccountId(15L, 1L))
                .thenReturn(Optional.of(card(CardStatus.ACTIVE, LocalDate.now().plusYears(5))));
        when(balanceRepository.findById(1L)).thenReturn(Optional.of(balance));
        transactionGetsId();

        BalanceResponse response = balanceService.withdraw(1L, new WithdrawRequest(new BigDecimal("200000"), null, 15L));

        assertThat(response.availableBalance()).isEqualByComparingTo("800000");
    }

    @Test
    @DisplayName("API 11 ❌ Không đủ số dư → INSUFFICIENT_BALANCE, số dư giữ nguyên")
    void withdraw_insufficientBalance() {
        Balance balance = balance("100000");
        when(cardRepository.findByIdAndAccountId(15L, 1L))
                .thenReturn(Optional.of(card(CardStatus.ACTIVE, LocalDate.now().plusYears(5))));
        when(balanceRepository.findById(1L)).thenReturn(Optional.of(balance));

        assertThatThrownBy(() -> balanceService.withdraw(1L, new WithdrawRequest(new BigDecimal("200000"), null, 15L)))
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);
        assertThat(balance.getAvailableBalance()).isEqualByComparingTo("100000");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("API 11 ❌ Thẻ ACTIVE nhưng hết hạn (như tài khoản D) → CARD_NOT_ACTIVE")
    void withdraw_expiredCard() {
        when(cardRepository.findByIdAndAccountId(15L, 1L))
                .thenReturn(Optional.of(card(CardStatus.ACTIVE, LocalDate.of(2020, 1, 31))));

        assertThatThrownBy(() -> balanceService.withdraw(1L, new WithdrawRequest(new BigDecimal("1000"), null, 15L)))
                .extracting("errorCode").isEqualTo(ErrorCode.CARD_NOT_ACTIVE);
        verify(balanceRepository, never()).findById(any());
    }
}
