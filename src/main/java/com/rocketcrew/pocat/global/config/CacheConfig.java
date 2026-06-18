package com.rocketcrew.pocat.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.global.cache.CacheNames;
import com.rocketcrew.pocat.global.cache.RedisCacheErrorHandler;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    @Bean
    public RedisCacheManager redisCacheManager(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper
    ) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                redisCacheSerializer(objectMapper)
                        )
                )
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigurations = Map.of(
                CacheNames.USER_PROFILE, defaultConfig.entryTtl(Duration.ofMinutes(30)),
                CacheNames.AUCTION_BID_HISTORY, defaultConfig.entryTtl(Duration.ofHours(24)),
                CacheNames.POST_FREE_DETAIL, defaultConfig.entryTtl(Duration.ofMinutes(10)),
                CacheNames.POST_TRADE_DETAIL, defaultConfig.entryTtl(Duration.ofMinutes(10)),
                CacheNames.USER_BID_BLOCKED, defaultConfig.entryTtl(Duration.ofMinutes(60)),
                CacheNames.POST_COMMENTS, defaultConfig.entryTtl(Duration.ofMinutes(10))
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new RedisCacheErrorHandler();
    }

    private GenericJackson2JsonRedisSerializer redisCacheSerializer(ObjectMapper objectMapper) {
        return GenericJackson2JsonRedisSerializer.builder()
                .objectMapper(objectMapper.copy())
                .defaultTyping(true)
                .build();
    }
}
