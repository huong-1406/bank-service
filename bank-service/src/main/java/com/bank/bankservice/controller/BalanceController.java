package com.bank.bankservice.controller;

import com.bank.bankservice.dto.request.DepositRequest;
import com.bank.bankservice.dto.request.WithdrawRequest;
import com.bank.bankservice.dto.response.BalanceResponse;
import com.bank.bankservice.security.SecurityUtil;
import com.bank.bankservice.service.BalanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BalanceController {

    private final BalanceService balanceService;

    @GetMapping("/accounts/me/balance")
    public BalanceResponse getMyBalance() {
        return balanceService.getBalance(SecurityUtil.currentAccountId());
    }

    @PostMapping("/balance/deposit")
    public BalanceResponse deposit(@Valid @RequestBody DepositRequest request) {
        return balanceService.deposit(SecurityUtil.currentAccountId(), request);
    }

    @PostMapping("/balance/withdraw")
    public BalanceResponse withdraw(@Valid @RequestBody WithdrawRequest request) {
        return balanceService.withdraw(SecurityUtil.currentAccountId(), request);
    }
}
