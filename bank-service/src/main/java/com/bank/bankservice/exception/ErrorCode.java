package com.bank.bankservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

// Bảng mã lỗi — khớp API.md §2. Frontend hiển thị theo code, không theo message
@Getter
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ"),
    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "Số dư khả dụng không đủ"),
    CARD_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "Thẻ không hoạt động hoặc đã hết hạn"),
    CARD_HAS_PENDING_TRANSACTION(HttpStatus.BAD_REQUEST, "Thẻ còn giao dịch đang chờ xử lý, không xoá được"),
    ACCOUNT_HAS_CARDS(HttpStatus.BAD_REQUEST, "Tài khoản còn thẻ liên kết, không xoá được"),
    ACCOUNT_BALANCE_NOT_ZERO(HttpStatus.BAD_REQUEST, "Số dư khác 0, không xoá được"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập hoặc token không hợp lệ"),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản"),
    CARD_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy thẻ"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy đường dẫn"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Phương thức HTTP không được hỗ trợ"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email đã được sử dụng"),
    PHONE_ALREADY_EXISTS(HttpStatus.CONFLICT, "Số điện thoại đã được sử dụng"),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT, "Số dư vừa bị thay đổi bởi giao dịch khác, vui lòng thử lại"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
