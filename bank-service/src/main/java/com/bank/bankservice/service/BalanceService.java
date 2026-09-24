package com.bank.bankservice.service;

import com.bank.bankservice.dto.request.DepositRequest;
import com.bank.bankservice.dto.request.WithdrawRequest;
import com.bank.bankservice.dto.response.BalanceResponse;

public interface BalanceService {

    BalanceResponse getBalance(Long accountId);

    BalanceResponse deposit(Long accountId, DepositRequest request);

    BalanceResponse withdraw(Long accountId, WithdrawRequest request);
}
