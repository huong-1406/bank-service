package com.bank.paymentservice.config;

import com.bank.paymentservice.messaging.PaymentMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

import java.util.Map;

@Configuration
public class JmsConfig {

    // Tên hộp thư. notification-service phải nghe đúng tên này
    public static final String PAYMENT_QUEUE = "payment.queue";

    // Gửi message dạng JSON: đọc được trên trang ActiveMQ, và bên nhận không cần class Java giống hệt.
    // "_type" = "payment" là nhãn để bên nhận biết đổi JSON về class nào
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
