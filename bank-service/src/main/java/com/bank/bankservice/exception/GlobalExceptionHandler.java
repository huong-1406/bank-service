package com.bank.bankservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request) {
        return build(ex.getErrorCode(), request, null);
    }

    // Lỗi @Valid: trả kèm lỗi của từng trường
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return build(ErrorCode.VALIDATION_FAILED, request, fieldErrors);
    }

    // JSON sai cú pháp, enum sai giá trị (cardType = "ABC"), ngày sai định dạng...
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleUnreadable(Exception ex, HttpServletRequest request) {
        return build(ErrorCode.VALIDATION_FAILED, request, null);
    }

    // Cột version của balance: hai lệnh ghi cùng lúc thì lệnh sau rơi vào đây
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        return build(ErrorCode.CONCURRENT_UPDATE, request, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(ErrorCode.NOT_FOUND, request, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Lỗi không mong muốn tại {}", request.getRequestURI(), ex);
        return build(ErrorCode.INTERNAL_ERROR, request, null);
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode code, HttpServletRequest request, Map<String, String> fieldErrors) {
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, request.getRequestURI(), fieldErrors));
    }
}
