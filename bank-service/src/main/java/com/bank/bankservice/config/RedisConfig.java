package com.bank.bankservice.config;

import com.bank.bankservice.dto.response.AccountResponse;
import com.bank.bankservice.dto.response.BalanceResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;
import java.util.Map;

// Cache-aside cho thông tin tài khoản và số dư — ARCHITECTURE2.md §8
@Configuration
@EnableCaching
public class RedisConfig {

    public static final String ACCOUNT_CACHE = "account";
    public static final String BALANCE_CACHE = "balance";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper,
                                          @Value("${spring.cache.redis.time-to-live}") Duration ttl) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues();

        // Lưu JSON (mở redis-cli đọc được), mỗi cache khai báo đúng kiểu để đọc ra lại đúng object
        Map<String, RedisCacheConfiguration> caches = Map.of(
                ACCOUNT_CACHE, defaults.serializeValuesWith(json(objectMapper, AccountResponse.class)),
                BALANCE_CACHE, defaults.serializeValuesWith(json(objectMapper, BalanceResponse.class)));

        return new ResilientRedisCacheManager(RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory),
                defaults, caches);
    }

    private static <T> SerializationPair<T> json(ObjectMapper objectMapper, Class<T> type) {
        return SerializationPair.fromSerializer(new Jackson2JsonRedisSerializer<>(objectMapper, type));
    }
}
