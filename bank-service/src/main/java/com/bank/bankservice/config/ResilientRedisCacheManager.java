package com.bank.bankservice.config;

import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;

import java.util.Map;

/**
 * Mỗi cache được bọc 2 lớp:
 * 1. ResilientCache — Redis lỗi thì bỏ qua, API vẫn chạy bằng database.
 * 2. Transaction-aware — ghi / xoá cache chỉ chạy SAU khi transaction commit thành công.
 *    Commit lỗi (ví dụ 409 CONCURRENT_UPDATE) thì cache không bị đụng tới.
 *
 * Thứ tự bọc quan trọng: lệnh ghi bị dời ra sau commit, nên lớp bắt lỗi phải nằm TRONG,
 * không thì Redis chết sẽ làm lỗi 500 ở một giao dịch đã commit xong.
 */
public class ResilientRedisCacheManager extends RedisCacheManager {

    public ResilientRedisCacheManager(RedisCacheWriter cacheWriter, RedisCacheConfiguration defaults,
                                      Map<String, RedisCacheConfiguration> caches) {
        super(cacheWriter, defaults, caches);
        setTransactionAware(true);
    }

    @Override
    protected Cache decorateCache(Cache cache) {
        return super.decorateCache(new ResilientCache(cache));
    }
}
