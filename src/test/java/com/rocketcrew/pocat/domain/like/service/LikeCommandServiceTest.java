package com.rocketcrew.pocat.domain.like.service;

import com.rocketcrew.pocat.domain.like.repository.LikeRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RedissonClient;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED 테스트 — #133 동시성 제어
 *
 * 현재 LikeCommandService 에는:
 *   - RedissonClient 필드가 없음
 *   - ErrorCode.LIKE_LOCK_FAILED 가 없음
 *   - ErrorCode.RATE_LIMIT_EXCEEDED 가 없음
 *
 * 위 세 가지가 추가될 때까지 아래 테스트는 FAIL 해야 한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LikeCommandService — 동시성 제어 RED 테스트")
class LikeCommandServiceTest {

    @InjectMocks
    private LikeCommandService likeCommandService;

    @Mock
    private LikeRepository likeRepository;

    // RedissonClient mock — @InjectMocks 가 필드 주입 시 사용하게 될 빈
    @Mock
    private RedissonClient redissonClient;

    // ---------------------------------------------------------------
    // T1-1: RedissonClient 필드 주입 확인 (리플렉션)
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("RedissonClient 필드 주입")
    class RedissonClientInjection {

        @Test
        @DisplayName("LikeCommandService 에 RedissonClient 타입 필드가 선언되어 있어야 한다")
        void likeCommandService_hasRedissonClientField() {
            // when
            boolean hasRedissonField = Arrays.stream(LikeCommandService.class.getDeclaredFields())
                    .anyMatch(f -> f.getType().equals(RedissonClient.class));

            // then — FAILS until RedissonClient is injected into LikeCommandService
            assertThat(hasRedissonField)
                    .as("LikeCommandService must declare a RedissonClient field for distributed locking")
                    .isTrue();
        }

        @Test
        @DisplayName("RedissonClient 필드가 실제로 주입되어 있어야 한다 (null 아님)")
        void likeCommandService_redissonClientFieldIsNotNull() throws Exception {
            // given: find the RedissonClient field (will throw NoSuchFieldException if absent)
            Field redissonField = Arrays.stream(LikeCommandService.class.getDeclaredFields())
                    .filter(f -> f.getType().equals(RedissonClient.class))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "RedissonClient field not found in LikeCommandService — add it for distributed locking"));

            redissonField.setAccessible(true);
            Object value = redissonField.get(likeCommandService);

            // then — FAILS until Mockito can inject into the declared field
            assertThat(value)
                    .as("RedissonClient field must not be null — @InjectMocks should inject the mock")
                    .isNotNull();
        }
    }

    // ---------------------------------------------------------------
    // T1-2: ErrorCode.LIKE_LOCK_FAILED 존재 확인
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("ErrorCode 열거값 — Like 락 실패")
    class LikeLockFailedErrorCode {

        @Test
        @DisplayName("ErrorCode.LIKE_LOCK_FAILED 열거값이 존재해야 한다")
        void errorCode_LIKE_LOCK_FAILED_exists() {
            // when
            boolean exists = Arrays.stream(ErrorCode.values())
                    .anyMatch(e -> e.name().equals("LIKE_LOCK_FAILED"));

            // then — FAILS until LIKE_LOCK_FAILED is added to ErrorCode enum
            assertThat(exists)
                    .as("ErrorCode.LIKE_LOCK_FAILED must be declared for concurrent like/unlike protection")
                    .isTrue();
        }
    }

    // ---------------------------------------------------------------
    // T1-3: ErrorCode.RATE_LIMIT_EXCEEDED 존재 확인
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("ErrorCode 열거값 — Rate Limit 초과")
    class RateLimitExceededErrorCode {

        @Test
        @DisplayName("ErrorCode.RATE_LIMIT_EXCEEDED 열거값이 존재해야 한다")
        void errorCode_RATE_LIMIT_EXCEEDED_exists() {
            // when
            boolean exists = Arrays.stream(ErrorCode.values())
                    .anyMatch(e -> e.name().equals("RATE_LIMIT_EXCEEDED"));

            // then — FAILS until RATE_LIMIT_EXCEEDED is added to ErrorCode enum
            assertThat(exists)
                    .as("ErrorCode.RATE_LIMIT_EXCEEDED must be declared for Redis-based rate limiting")
                    .isTrue();
        }
    }
}
