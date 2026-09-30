package com.bank.bankservice.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;

// Gọi HTTP sang payment-service. Không phải Controller/Service/Repository: đây là phần nói chuyện với service khác
@Component
public class PaymentClient {

    private final RestClient restClient;

    public PaymentClient(@Value("${payment-service.url}") String baseUrl,
                         @Value("${payment-service.timeout}") Duration timeout) {
        // Giới hạn thời gian chờ: payment-service treo thì không bắt người dùng chờ mãi
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    // Dữ liệu gửi sang payment-service — API.md "API nội bộ"
    public record PaymentServiceRequest(Long paymentId, Long accountId, BigDecimal amount, String currency) {
    }

    // payment-service trả 202 là thành công. Lỗi kết nối, quá giờ hoặc mã lỗi → ném RestClientException
    public void sendPayment(PaymentServiceRequest request) {
        restClient.post()
                .uri("/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }
}
