package com.rocketcrew.pocat.cache;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 테스트용 Redis 인프라 Mock 설정 — 통합 테스트 전용.
 *
 * RedisConnectionFactory · RedisMessageListenerContainer는 각 통합 테스트의 @MockBean으로 교체해
 * 실제 Redis 없이 Spring Context를 기동시킨다.
 * CacheManager는 @Cacheable 동작 검증용 in-memory 구현으로 제공한다.
 */
@TestConfiguration
public class MockRedisTestConfig {

    @Bean
    @Primary
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
                "user:profile", "user:bid-blocked",
                "post:free:detail", "post:trade:detail",
                "auction:bid-history", "post:comments");
    }
}
