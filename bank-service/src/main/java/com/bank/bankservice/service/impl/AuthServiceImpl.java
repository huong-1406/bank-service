package com.bank.bankservice.service.impl;

import com.bank.bankservice.dto.request.LoginRequest;
import com.bank.bankservice.dto.response.LoginResponse;
import com.bank.bankservice.entity.Account;
import com.bank.bankservice.exception.BusinessException;
import com.bank.bankservice.exception.ErrorCode;
import com.bank.bankservice.repository.AccountRepository;
import com.bank.bankservice.security.JwtUtil;
import com.bank.bankservice.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        // Sai email và sai mật khẩu trả cùng một lỗi, để không lộ email nào đã đăng ký
        Account account = accountRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(request.password(), account.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        return LoginResponse.bearer(jwtUtil.generateToken(account.getId()), jwtUtil.getExpirationSeconds());
    }
}
