package com.rocketcrew.pocat.global.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

@Slf4j
public class RedisCacheErrorHandler implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
        log.warn("[CACHE] 캐시 조회 실패 cache={} key={}: {}", cache.getName(), key, ex.getMessage());
    }

    @Override
    public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
        log.warn("[CACHE] 캐시 저장 실패 cache={} key={}: {}", cache.getName(), key, ex.getMessage());
    }

    @Override
    public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
        log.warn("[CACHE] 캐시 삭제 실패 cache={} key={}: {}", cache.getName(), key, ex.getMessage());
    }

    @Override
    public void handleCacheClearError(RuntimeException ex, Cache cache) {
        log.warn("[CACHE] 캐시 전체 삭제 실패 cache={}: {}", cache.getName(), ex.getMessage());
    }
}
