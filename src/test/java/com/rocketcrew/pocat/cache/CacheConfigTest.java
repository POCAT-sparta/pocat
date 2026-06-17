package com.rocketcrew.pocat.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.ActiveProfiles;
import com.rocketcrew.pocat.domain.auction.redis.AuctionExpirationRedisSubscriber;
import com.rocketcrew.pocat.domain.auction.service.AuctionEsIndexService;
import com.rocketcrew.pocat.domain.order.service.ExpiryEventListener;
import com.rocketcrew.pocat.support.MockElasticsearchTestConfig;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-01: CacheConfig 빈 등록 검증
 *
 * 검증 항목:
 * - ApplicationContext의 CacheManager가 RedisCacheManager 타입인지
 * - 캐시 이름들(user:profile, user:nickname, comment:list)이 등록되는지
 *
 * 현재 CacheConfig/RedisCacheManager Bean 미존재 + @EnableCaching 미적용 → FAIL 예상
 * - Spring Boot는 @EnableCaching 없이 CacheManager Bean을 자동 생성하지 않음
 * - CacheManager를 직접 조회했을 때 RedisCacheManager 타입이 아님 → FAIL
 *
 * @MockBean으로 Redis 인프라 빈을 교체하여 Context 기동 가능하게 하되,
 * CacheManager는 교체하지 않아 T-01 의도적 FAIL 유도.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(MockElasticsearchTestConfig.class)
class CacheConfigTest {

    // Redis 인프라 빈을 @MockBean으로 교체 (실제 연결 방지)
    @MockBean
    private RedissonClient redissonClient;

    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @MockBean
    private AuctionEsIndexService auctionEsIndexService;

    @MockBean
    private AuctionExpirationRedisSubscriber auctionExpirationRedisSubscriber;

    @MockBean
    private ExpiryEventListener expiryEventListener;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("T-01-1: CacheManager가 RedisCacheManager 타입이어야 한다")
    void cacheManagerShouldBeRedisCacheManager() {
        // 현재 @EnableCaching + CacheConfig 미구현 → CacheManager Bean 없거나 기본 구현체
        // RedisCacheManager 타입이 아니면 FAIL
        CacheManager cacheManager = applicationContext.getBean(CacheManager.class);
        assertThat(cacheManager)
                .as("CacheManager는 RedisCacheManager 여야 하지만 실제 타입: %s",
                        cacheManager.getClass().getSimpleName())
                .isInstanceOf(RedisCacheManager.class);
    }

    @Test
    @DisplayName("T-01-2: user:profile 캐시 이름이 RedisCacheManager에 등록되어야 한다")
    void userProfileCacheShouldBeRegistered() {
        CacheManager cacheManager = applicationContext.getBean(CacheManager.class);
        assertThat(cacheManager.getCacheNames())
                .as("캐시 이름에 'user:profile' 이 포함되어야 한다")
                .contains("user:profile");
    }

    @Test
    @DisplayName("T-01-3: user:nickname은 수동 Cache-Aside로 관리되므로 RedisCacheManager에 등록되지 않는다")
    void userNicknameShouldNotBeInRedisCacheManager() {
        CacheManager cacheManager = applicationContext.getBean(CacheManager.class);
        assertThat(cacheManager.getCacheNames())
                .as("user:nickname은 수동 Cache-Aside(UserNicknameCacheService)로 관리되므로 RedisCacheManager에 없어야 한다")
                .doesNotContain("user:nickname");
    }

    @Test
    @DisplayName("T-01-4: post:comments 캐시 이름이 RedisCacheManager에 등록되어야 한다")
    void commentListCacheShouldBeRegistered() {
        CacheManager cacheManager = applicationContext.getBean(CacheManager.class);
        assertThat(cacheManager.getCacheNames())
                .as("캐시 이름에 'post:comments' 이 포함되어야 한다")
                .contains("post:comments");
    }
}
