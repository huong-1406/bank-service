package com.bank.bankservice.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Đề bài chỉ cho sửa email và số điện thoại. Gửi trường nào sửa trường đó
public record UpdateAccountRequest(
        @Email(message = "Email không đúng định dạng")
        @Size(max = 150, message = "Email tối đa 150 ký tự")
        String email,

        @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại phải có 10 chữ số, bắt đầu bằng 0")
        String phoneNumber
) {
    @AssertTrue(message = "Phải gửi ít nhất email hoặc số điện thoại")
    public boolean isAnyFieldPresent() {
        return email != null || phoneNumber != null;
    }
}
