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
import com.bank.bankservice.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final BalanceRepository balanceRepository;
    private final CardRepository cardRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        if (accountRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (accountRepository.existsByPhoneNumber(request.phoneNumber())) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_EXISTS);
        }

        Account account = new Account();
        account.setCustomerName(request.customerName());
        account.setEmail(request.email());
        account.setPhoneNumber(request.phoneNumber());
        account.setPassword(passwordEncoder.encode(request.password()));
        accountRepository.save(account);

        // Tạo số dư = 0 trong cùng transaction: lỗi ở đây thì tài khoản cũng không được lưu
        Balance balance = new Balance();
        balance.setAccount(account);
        balanceRepository.save(balance);

        return AccountResponse.from(account);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccount(Long accountId) {
        Account account = findAccount(accountId);
        return AccountResponse.from(account, findBalance(accountId));
    }

    @Override
    @Transactional
    public AccountResponse updateAccount(Long accountId, UpdateAccountRequest request) {
        Account account = findAccount(accountId);

        // Chỉ kiểm tra trùng khi giá trị thật sự đổi; gửi lại email cũ của chính mình vẫn hợp lệ
        if (request.email() != null && !request.email().equals(account.getEmail())) {
            if (accountRepository.existsByEmail(request.email())) {
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }
            account.setEmail(request.email());
        }
        if (request.phoneNumber() != null && !request.phoneNumber().equals(account.getPhoneNumber())) {
            if (accountRepository.existsByPhoneNumber(request.phoneNumber())) {
                throw new BusinessException(ErrorCode.PHONE_ALREADY_EXISTS);
            }
            account.setPhoneNumber(request.phoneNumber());
        }

        return AccountResponse.from(account, findBalance(accountId));
    }

    @Override
    @Transactional
    public void deleteAccount(Long accountId) {
        Account account = findAccount(accountId);

        // Đề bài: chỉ xoá khi không còn thẻ liên kết VÀ số dư bằng 0
        if (cardRepository.existsByAccountId(accountId)) {
            throw new BusinessException(ErrorCode.ACCOUNT_HAS_CARDS);
        }
        // Tiền đang bị giữ cũng là tiền: kiểm tra cả available lẫn hold
        Balance balance = findBalance(accountId);
        if (balance.getAvailableBalance().compareTo(BigDecimal.ZERO) != 0
                || balance.getHoldBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_BALANCE_NOT_ZERO);
        }

        // Phải xoá balance trước: balance đang nằm trong bộ nhớ Hibernate và trỏ tới account,
        // để nguyên thì Hibernate lặng lẽ bỏ lệnh xoá account. transaction thì DB tự xoá theo (CASCADE)
        balanceRepository.delete(balance);
        accountRepository.delete(account);
    }

    private Account findAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private Balance findBalance(Long accountId) {
        return balanceRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
    }
}
