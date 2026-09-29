package com.bank.notificationservice.config;

import com.bank.notificationservice.messaging.PaymentMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

import java.util.Map;

@Configuration
public class JmsConfig {

    // Phải trùng tên hàng đợi bên payment-service
    public static final String PAYMENT_QUEUE = "payment.queue";

    // Đọc message JSON. Nhãn "_type" = "payment" (payment-service gắn khi gửi) → đổi về PaymentMessage
    @Bean
    public MessageConverter jacksonJmsMessageConverter(ObjectMapper objectMapper) {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        converter.setTargetType(MessageType.TEXT);
        converter.setTypeIdPropertyName("_type");
        converter.setTypeIdMappings(Map.of("payment", PaymentMessage.class));
        return converter;
    }
}
