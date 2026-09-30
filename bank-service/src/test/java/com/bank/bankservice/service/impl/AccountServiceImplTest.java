package com.bank.bankservice.service.impl;

import com.bank.bankservice.dto.request.CreateAccountRequest;
import com.bank.bankservice.dto.request.UpdateAccountRequest;
import com.bank.bankservice.dto.response.AccountResponse;
import com.bank.bankservice.entity.Account;
import com.bank.bankservice.entity.Balance;
import com.bank.bankservice.exception.BusinessException;
import com.bank.bankservice.exception.ErrorCode;
import com.bank.bankservice.repository.AccountRepository;
import com.bank.bankservice.repository.BalanceRepository;
import com.bank.bankservice.repository.CardRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// API 2, 3, 4, 5: đăng ký, xem, sửa, xoá tài khoản
@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    @Mock private AccountRepository accountRepository;
    @Mock private BalanceRepository balanceRepository;
    @Mock private CardRepository cardRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private AccountServiceImpl accountService;

    private Account account() {
        Account account = new Account();
        account.setId(1L);
        account.setCustomerName("Nguyễn Văn A");
        account.setEmail("a@test.com");
        account.setPhoneNumber("0900000001");
        return account;
    }

    private Balance balance(String available, String hold) {
        Balance balance = new Balance();
        balance.setAccountId(1L);
        balance.setAvailableBalance(new BigDecimal(available));
        balance.setHoldBalance(new BigDecimal(hold));
        return balance;
    }

    // ===== API 2: đăng ký =====
    @Test
    @DisplayName("API 2 ✅ Đăng ký: mã hoá mật khẩu, tự tạo số dư")
    void createAccount_success() {
        var request = new CreateAccountRequest("Test User", "new@test.com", "0987654321", "Test@1234");
        when(accountRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(accountRepository.existsByPhoneNumber("0987654321")).thenReturn(false);
        when(passwordEncoder.encode("Test@1234")).thenReturn("hash");

        AccountResponse response = accountService.createAccount(request);

        assertThat(response.email()).isEqualTo("new@test.com");
        verify(accountRepository).save(any(Account.class));
        verify(balanceRepository).save(any(Balance.class));
    }

    @Test
    @DisplayName("API 2 ❌ Đăng ký trùng email → EMAIL_ALREADY_EXISTS, không lưu")
    void createAccount_duplicateEmail() {
        var request = new CreateAccountRequest("Test User", "a@test.com", "0987654321", "Test@1234");
        when(accountRepository.existsByEmail("a@test.com")).thenReturn(true);

        assertThatThrownBy(() -> accountService.createAccount(request))
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
        verify(accountRepository, never()).save(any());
    }

    // ===== API 3: xem tài khoản =====
    @Test
    @DisplayName("API 3 ✅ Xem tài khoản: trả kèm số dư")
    void getAccount_success() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account()));
        when(balanceRepository.findById(1L)).thenReturn(Optional.of(balance("1000000", "0")));

        AccountResponse response = accountService.getAccount(1L);

        assertThat(response.email()).isEqualTo("a@test.com");
        assertThat(response.balance().availableBalance()).isEqualByComparingTo("1000000");
    }

    @Test
    @DisplayName("API 3 ❌ Tài khoản không tồn tại → ACCOUNT_NOT_FOUND")
    void getAccount_notFound() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccount(99L))
                .extracting("errorCode").isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
    }

    // ===== API 4: sửa tài khoản =====
    @Test
    @DisplayName("API 4 ✅ Sửa số điện thoại")
    void updateAccount_success() {
        Account account = account();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountRepository.existsByPhoneNumber("0977777777")).thenReturn(false);
        when(balanceRepository.findById(1L)).thenReturn(Optional.of(balance("0", "0")));

        AccountResponse response = accountService.updateAccount(1L, new UpdateAccountRequest(null, "0977777777"));

        assertThat(response.phoneNumber()).isEqualTo("0977777777");
        assertThat(account.getPhoneNumber()).isEqualTo("0977777777");
    }

    @Test
    @DisplayName("API 4 ❌ Đổi sang email đã có người dùng → EMAIL_ALREADY_EXISTS")
    void updateAccount_duplicateEmail() {
        Account account = account();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountRepository.existsByEmail("b@test.com")).thenReturn(true);

        assertThatThrownBy(() -> accountService.updateAccount(1L, new UpdateAccountRequest("b@test.com", null)))
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
        assertThat(account.getEmail()).isEqualTo("a@test.com");
    }

    // ===== API 5: xoá tài khoản =====
    @Test
    @DisplayName("API 5 ✅ Xoá tài khoản không còn thẻ, số dư 0")
    void deleteAccount_success() {
        Account account = account();
        Balance balance = balance("0", "0");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(cardRepository.existsByAccountId(1L)).thenReturn(false);
        when(balanceRepository.findById(1L)).thenReturn(Optional.of(balance));

        accountService.deleteAccount(1L);

        verify(balanceRepository).delete(balance);
        verify(accountRepository).delete(account);
    }

    @Test
    @DisplayName("API 5 ❌ Tài khoản còn thẻ → ACCOUNT_HAS_CARDS, không xoá")
    void deleteAccount_hasCards() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account()));
        when(cardRepository.existsByAccountId(1L)).thenReturn(true);

        assertThatThrownBy(() -> accountService.deleteAccount(1L))
                .extracting("errorCode").isEqualTo(ErrorCode.ACCOUNT_HAS_CARDS);
        verify(accountRepository, never()).delete(any());
    }
}
