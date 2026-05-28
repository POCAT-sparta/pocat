package com.rocketcrew.pocat.global.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * RED 테스트 — #133 RedisRateLimiter 단위 테스트
 *
 * 현재 com.rocketcrew.pocat.global.ratelimit 패키지에:
 *   - RedisRateLimiter 클래스가 없음
 *   - RateLimitProperties 클래스가 없음
 *
 * 위 두 클래스가 생성될 때까지 아래 테스트는 FAIL 해야 한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RedisRateLimiter — 존재 및 API 계약 RED 테스트")
class RedisRateLimiterTest {

    private static final String RATE_LIMITER_CLASS =
            "com.rocketcrew.pocat.global.ratelimit.RedisRateLimiter";

    private static final String RATE_LIMIT_PROPERTIES_CLASS =
            "com.rocketcrew.pocat.global.ratelimit.RateLimitProperties";

    // ---------------------------------------------------------------
    // T2-1: RedisRateLimiter 클래스 존재 + isAllowed 메서드 시그니처
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("RedisRateLimiter 클래스")
    class RedisRateLimiterClass {

        @Test
        @DisplayName("RedisRateLimiter 클래스가 존재해야 한다")
        void redisRateLimiter_classExists() {
            // then — FAILS until RedisRateLimiter is created
            assertThatCode(() -> Class.forName(RATE_LIMITER_CLASS))
                    .as("RedisRateLimiter class must exist in com.rocketcrew.pocat.global.ratelimit")
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("isAllowed(String key, int limit, long windowSeconds) 메서드가 존재해야 한다")
        void redisRateLimiter_isAllowedMethodExists() throws Exception {
            // given
            Class<?> clazz;
            try {
                clazz = Class.forName(RATE_LIMITER_CLASS);
            } catch (ClassNotFoundException e) {
                throw new AssertionError(
                        "RedisRateLimiter class not found — create it first", e);
            }

            // when
            Method isAllowed;
            try {
                isAllowed = clazz.getDeclaredMethod("isAllowed", String.class, int.class, long.class);
            } catch (NoSuchMethodException e) {
                throw new AssertionError(
                        "RedisRateLimiter must declare isAllowed(String key, int limit, long windowSeconds)", e);
            }

            // then
            assertThat(isAllowed.getReturnType())
                    .as("isAllowed() must return boolean")
                    .isEqualTo(boolean.class);
        }

        @Test
        @DisplayName("RedisRateLimiter 는 @Component 또는 @Service 로 스프링 빈 등록되어야 한다")
        void redisRateLimiter_isSpringBean() throws Exception {
            Class<?> clazz;
            try {
                clazz = Class.forName(RATE_LIMITER_CLASS);
            } catch (ClassNotFoundException e) {
                throw new AssertionError(
                        "RedisRateLimiter class not found — create it first", e);
            }

            boolean isComponent = clazz.isAnnotationPresent(
                    org.springframework.stereotype.Component.class);
            boolean isService = clazz.isAnnotationPresent(
                    org.springframework.stereotype.Service.class);

            // then — FAILS until @Component or @Service is added
            assertThat(isComponent || isService)
                    .as("RedisRateLimiter must be annotated with @Component or @Service")
                    .isTrue();
        }
    }

    // ---------------------------------------------------------------
    // T2-2: RateLimitProperties 클래스 존재 확인
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("RateLimitProperties 클래스")
    class RateLimitPropertiesClass {

        @Test
        @DisplayName("RateLimitProperties 클래스가 존재해야 한다")
        void rateLimitProperties_classExists() {
            // then — FAILS until RateLimitProperties is created
            assertThatCode(() -> Class.forName(RATE_LIMIT_PROPERTIES_CLASS))
                    .as("RateLimitProperties class must exist in com.rocketcrew.pocat.global.ratelimit")
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("RateLimitProperties 는 @ConfigurationProperties 로 선언되어야 한다")
        void rateLimitProperties_isConfigurationProperties() throws Exception {
            Class<?> clazz;
            try {
                clazz = Class.forName(RATE_LIMIT_PROPERTIES_CLASS);
            } catch (ClassNotFoundException e) {
                throw new AssertionError(
                        "RateLimitProperties class not found — create it first", e);
            }

            boolean hasConfigProps = clazz.isAnnotationPresent(
                    org.springframework.boot.context.properties.ConfigurationProperties.class);

            // then — FAILS until @ConfigurationProperties is declared
            assertThat(hasConfigProps)
                    .as("RateLimitProperties must be annotated with @ConfigurationProperties")
                    .isTrue();
        }
    }
}
