package com.bank.bankservice.controller;

import com.bank.bankservice.dto.request.CreateAccountRequest;
import com.bank.bankservice.dto.request.UpdateAccountRequest;
import com.bank.bankservice.dto.response.AccountResponse;
import com.bank.bankservice.security.SecurityUtil;
import com.bank.bankservice.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// accountId luôn lấy từ JWT qua SecurityUtil, không bao giờ nhận từ URL hay body
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse createAccount(@Valid @RequestBody CreateAccountRequest request) {
        return accountService.createAccount(request);
    }

    @GetMapping("/me")
    public AccountResponse getMyAccount() {
        return accountService.getAccount(SecurityUtil.currentAccountId());
    }

    @PutMapping("/me")
    public AccountResponse updateMyAccount(@Valid @RequestBody UpdateAccountRequest request) {
        return accountService.updateAccount(SecurityUtil.currentAccountId(), request);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMyAccount() {
        accountService.deleteAccount(SecurityUtil.currentAccountId());
    }
}
