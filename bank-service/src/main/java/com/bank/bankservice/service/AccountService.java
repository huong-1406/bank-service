package com.bank.bankservice.service;

import com.bank.bankservice.dto.request.CreateAccountRequest;
import com.bank.bankservice.dto.request.UpdateAccountRequest;
import com.bank.bankservice.dto.response.AccountResponse;

public interface AccountService {

    AccountResponse createAccount(CreateAccountRequest request);

    AccountResponse getAccount(Long accountId);

    AccountResponse updateAccount(Long accountId, UpdateAccountRequest request);

    void deleteAccount(Long accountId);
}
