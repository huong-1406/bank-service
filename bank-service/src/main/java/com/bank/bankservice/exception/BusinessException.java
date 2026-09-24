package com.bank.bankservice.exception;

import lombok.Getter;

// Service ném exception này khi vi phạm luật nghiệp vụ; GlobalExceptionHandler đổi thành JSON lỗi
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
