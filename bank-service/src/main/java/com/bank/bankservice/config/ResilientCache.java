package com.bank.bankservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;

import java.util.concurrent.Callable;

// Redis chỉ là bản sao để đọc nhanh: lỗi thì ghi log rồi coi như cache trống, không làm hỏng API
@Slf4j
@RequiredArgsConstructor
public class ResilientCache implements Cache {

    private final Cache delegate;

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Object getNativeCache() {
        return delegate.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
        try {
            return delegate.get(key);
        } catch (RuntimeException e) {
            warn("đọc", key, e);
            return null;
        }
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        try {
            return delegate.get(key, type);
        } catch (RuntimeException e) {
            warn("đọc", key, e);
            return null;
        }
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        try {
            return delegate.get(key, valueLoader);
        } catch (RuntimeException e) {
            warn("đọc", key, e);
            try {
                return valueLoader.call();
            } catch (Exception loaderError) {
                throw new ValueRetrievalException(key, valueLoader, loaderError);
            }
        }
    }

    @Override
    public void put(Object key, Object value) {
        try {
            delegate.put(key, value);
        } catch (RuntimeException e) {
            warn("ghi", key, e);
        }
    }

    @Override
    public void evict(Object key) {
        try {
            delegate.evict(key);
        } catch (RuntimeException e) {
            warn("xoá", key, e);
        }
    }

    @Override
    public void clear() {
        try {
            delegate.clear();
        } catch (RuntimeException e) {
            warn("xoá toàn bộ", "*", e);
        }
    }

    private void warn(String action, Object key, RuntimeException e) {
        log.warn("Redis lỗi khi {} cache {}::{} — bỏ qua cache, dùng database. Lý do: {}",
                action, getName(), key, e.getMessage());
    }
}
