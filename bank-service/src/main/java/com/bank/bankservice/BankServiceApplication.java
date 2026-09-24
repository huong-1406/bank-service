package com.bank.bankservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// Đăng nhập tự làm bằng JWT, không dùng user mặc định mà Spring Security tự sinh
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class BankServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BankServiceApplication.class, args);
    }
}
