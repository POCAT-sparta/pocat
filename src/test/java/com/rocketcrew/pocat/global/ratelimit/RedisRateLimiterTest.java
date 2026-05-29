package com.rocketcrew.pocat.global.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisRateLimiter — 동작 테스트")
class RedisRateLimiterTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @InjectMocks
    private RedisRateLimiter redisRateLimiter;

    // ---------------------------------------------------------------
    // T1: 허용 (count < limit)
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("허용 케이스")
    class Allowed {

        @Test
        @DisplayName("성공: execute returns 0L → isAllowed returns true")
        void allowed_whenCountBelowLimit() {
            // given
            given(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                    .willReturn(0L);

            // when
            boolean result = redisRateLimiter.isAllowed("rate:user:like:1", 10, 60L);

            // then
            assertThat(result).isTrue();
        }
    }

    // ---------------------------------------------------------------
    // T2: 차단 (count > limit)
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("차단 케이스")
    class Blocked {

        @Test
        @DisplayName("실패: execute returns 1L → isAllowed returns false")
        void blocked_whenCountExceedsLimit() {
            // given
            given(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                    .willReturn(1L);

            // when
            boolean result = redisRateLimiter.isAllowed("rate:user:like:1", 10, 60L);

            // then
            assertThat(result).isFalse();
        }
    }

    // ---------------------------------------------------------------
    // T3: null 반환 (첫 번째 요청 edge case)
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("null 반환 케이스")
    class NullReturn {

        @Test
        @DisplayName("성공: execute returns null → isAllowed returns true (첫 번째 요청 edge case)")
        void allowed_whenResultIsNull() {
            // given
            given(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                    .willReturn(null);

            // when
            boolean result = redisRateLimiter.isAllowed("rate:user:like:1", 10, 60L);

            // then
            assertThat(result).isTrue();
        }
    }

    // ---------------------------------------------------------------
    // T4: Redis 장애 (fail-open)
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("Redis 장애 케이스")
    class RedisFailure {

        @Test
        @DisplayName("성공: execute throws RuntimeException → isAllowed returns true (fail-open)")
        void failOpen_whenRedisThrows() {
            // given
            given(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                    .willThrow(new RuntimeException("Redis connection refused"));

            // when
            boolean result = redisRateLimiter.isAllowed("rate:user:like:1", 10, 60L);

            // then
            assertThat(result).isTrue();
        }
    }
}
