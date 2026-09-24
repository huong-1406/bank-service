package com.bank.bankservice.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    // Token chỉ chứa accountId — mọi API lấy accountId từ đây, không lấy từ URL hay body
    public String generateToken(Long accountId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(accountId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    // Ném JwtException nếu token sai chữ ký, hết hạn hoặc sai định dạng
    public Long parseAccountId(String token) {
        String subject = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException e) {
            throw new JwtException("Subject của token không phải accountId");
        }
    }

    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }
}
