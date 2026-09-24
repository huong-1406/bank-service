package com.bank.bankservice.dto.response;

public record LoginResponse(String token, String type, long expiresIn) {

    public static LoginResponse bearer(String token, long expiresIn) {
        return new LoginResponse(token, "Bearer", expiresIn);
    }
}
