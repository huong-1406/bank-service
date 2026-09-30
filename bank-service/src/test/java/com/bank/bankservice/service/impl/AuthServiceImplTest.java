package com.bank.bankservice.service.impl;

import com.bank.bankservice.dto.request.LoginRequest;
import com.bank.bankservice.dto.response.LoginResponse;
import com.bank.bankservice.entity.Account;
import com.bank.bankservice.exception.BusinessException;
import com.bank.bankservice.exception.ErrorCode;
import com.bank.bankservice.repository.AccountRepository;
import com.bank.bankservice.security.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// API 1: POST /auth/login
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private AccountRepository accountRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @InjectMocks private AuthServiceImpl authService;

    @Test
    @DisplayName("Đăng nhập đúng email và mật khẩu → trả token")
    void login_success() {
        Account account = new Account();
        account.setId(1L);
        account.setPassword("hash");
        when(accountRepository.findByEmail("a@test.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("Test@1234", "hash")).thenReturn(true);
        when(jwtUtil.generateToken(1L)).thenReturn("token-abc");
        when(jwtUtil.getExpirationSeconds()).thenReturn(3600L);

        LoginResponse response = authService.login(new LoginRequest("a@test.com", "Test@1234"));

        assertThat(response.token()).isEqualTo("token-abc");
        assertThat(response.expiresIn()).isEqualTo(3600L);
    }

    @Test
    @DisplayName("Sai mật khẩu → lỗi INVALID_CREDENTIALS, không tạo token")
    void login_wrongPassword() {
        Account account = new Account();
        account.setPassword("hash");
        when(accountRepository.findByEmail("a@test.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("sai12345", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("a@test.com", "sai12345")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        verify(jwtUtil, never()).generateToken(anyLong());
    }
}
