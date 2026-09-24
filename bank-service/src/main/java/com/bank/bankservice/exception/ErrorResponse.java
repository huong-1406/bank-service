package com.bank.bankservice.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

// Một định dạng lỗi duy nhất cho mọi API — API.md §2
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
    public static ErrorResponse of(ErrorCode errorCode, String path) {
        return of(errorCode, path, null);
    }

    public static ErrorResponse of(ErrorCode errorCode, String path, Map<String, String> fieldErrors) {
        return new ErrorResponse(LocalDateTime.now(), errorCode.getStatus().value(), errorCode.name(),
                errorCode.getMessage(), path, fieldErrors);
    }
}
