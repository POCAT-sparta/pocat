package com.rocketcrew.pocat.cache;

import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static org.mockito.Mockito.mock;

/**
 * 테스트용 Redis 인프라 Mock 설정 (T-02, T-03, T-04 전용).
 *
 * 실제 Redis 없이 Spring Context를 기동시키기 위해 Redis 관련 빈을 Mock/In-memory로 교체.
 * CacheManager는 ConcurrentMapCacheManager(in-memory)로 제공하여 @Cacheable 동작 검증.
 *
 * 주의: spring.main.allow-bean-definition-overriding=true 설정 필요하며,
 * @TestConfiguration은 main 설정보다 나중에 등록되어 override 됨.
 * 이를 방지하기 위해 @MockBean + @TestConfiguration 분리 전략 사용.
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
