package com.rocketcrew.pocat.cache;

import com.rocketcrew.pocat.domain.auction.redis.AuctionExpirationRedisSubscriber;
import com.rocketcrew.pocat.domain.order.service.ExpiryEventListener;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class MockRedisTestConfig {

    @MockBean
    AuctionExpirationRedisSubscriber auctionExpirationRedisSubscriber;

    @MockBean
    ExpiryEventListener expiryEventListener;

    @Bean
    @Primary
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
                "user:profile", "user:bid-blocked",
                "post:free:detail", "post:trade:detail",
                "auction:bid-history", "post:comments");
    }
}
