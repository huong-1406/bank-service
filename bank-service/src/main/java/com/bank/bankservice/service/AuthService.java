package com.bank.bankservice.service;

import com.bank.bankservice.dto.request.LoginRequest;
import com.bank.bankservice.dto.response.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);
}
