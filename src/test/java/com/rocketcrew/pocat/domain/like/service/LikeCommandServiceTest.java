package com.rocketcrew.pocat.domain.like.service;

import com.rocketcrew.pocat.domain.like.dto.response.ToggleLikeResponse;
import com.rocketcrew.pocat.domain.like.entity.Like;
import com.rocketcrew.pocat.domain.like.repository.LikeRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;
import com.rocketcrew.pocat.global.exception.domain.LikeException;
import com.rocketcrew.pocat.global.ratelimit.RateLimitProperties;
import com.rocketcrew.pocat.global.ratelimit.RedisRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LikeCommandService — 동시성 제어 동작 테스트")
class LikeCommandServiceTest {

    @InjectMocks
    private LikeCommandService likeCommandService;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @Mock
    private RedisRateLimiter redisRateLimiter;

    @Mock
    private RateLimitProperties rateLimitProperties;

    @BeforeEach
    void setUp() throws InterruptedException {
        given(rateLimitProperties.getLikeLimit()).willReturn(10);
        given(rateLimitProperties.getLikeWindowSeconds()).willReturn(60L);
        given(redisRateLimiter.isAllowed(anyString(), any(int.class), anyLong())).willReturn(true);
        given(redissonClient.getLock(anyString())).willReturn(rLock);
        given(rLock.tryLock(0L, TimeUnit.SECONDS)).willReturn(true);
        given(rLock.isHeldByCurrentThread()).willReturn(false);
    }

    // ---------------------------------------------------------------
    // T1: Rate Limit 초과
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("Rate Limit 초과")
    class RateLimitExceeded {

        @Test
        @DisplayName("실패: Rate Limit 초과 → ServiceException(RATE_LIMIT_EXCEEDED)")
        void fail_rateLimitExceeded() {
            // given
            given(redisRateLimiter.isAllowed(anyString(), any(int.class), anyLong())).willReturn(false);

            // when & then
            assertThatThrownBy(() -> likeCommandService.toggleLike(1L, 1L))
                    .isInstanceOf(ServiceException.class)
                    .satisfies(ex -> assertThat(((ServiceException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED));
        }
    }

    // ---------------------------------------------------------------
    // T2: 락 획득 실패
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("락 획득 실패")
    class LockAcquireFailed {

        @Test
        @DisplayName("실패: 락 획득 실패 → LikeException(LIKE_LOCK_FAILED)")
        void fail_lockAcquireFailed() throws InterruptedException {
            // given
            given(rLock.tryLock(0L, TimeUnit.SECONDS)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> likeCommandService.toggleLike(1L, 1L))
                    .isInstanceOf(LikeException.class)
                    .satisfies(ex -> assertThat(((LikeException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.LIKE_LOCK_FAILED));
        }
    }

    // ---------------------------------------------------------------
    // T3: 최초 좋아요
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("최초 좋아요")
    class FirstLike {

        @Test
        @DisplayName("성공: 최초 좋아요 → save 호출 후 isLiked=true 반환")
        void success_firstLike() {
            // given
            given(likeRepository.findByUserIdAndAuctionId(1L, 1L)).willReturn(Optional.empty());
            Like savedLike = Like.builder().userId(1L).auctionId(1L).build();
            given(likeRepository.save(any(Like.class))).willReturn(savedLike);

            // when
            ToggleLikeResponse response = likeCommandService.toggleLike(1L, 1L);

            // then
            assertThat(response.auctionId()).isEqualTo(1L);
            assertThat(response.isLiked()).isTrue();
            verify(likeRepository).save(any(Like.class));
        }
    }

    // ---------------------------------------------------------------
    // T4: 좋아요 취소
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("좋아요 취소")
    class CancelLike {

        @Test
        @DisplayName("성공: 이미 좋아요 → delete 호출 후 isLiked=false 반환")
        void success_cancelLike() {
            // given
            Like existingLike = Like.builder().userId(1L).auctionId(1L).build();
            given(likeRepository.findByUserIdAndAuctionId(1L, 1L)).willReturn(Optional.of(existingLike));

            // when
            ToggleLikeResponse response = likeCommandService.toggleLike(1L, 1L);

            // then
            assertThat(response.auctionId()).isEqualTo(1L);
            assertThat(response.isLiked()).isFalse();
            verify(likeRepository).delete(existingLike);
        }
    }

    // ---------------------------------------------------------------
    // T5: unique 제약 위반 (DataIntegrity - ConstraintViolation)
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("unique 제약 위반")
    class UniqueConstraintViolation {

        @Test
        @DisplayName("실패: save() 시 ConstraintViolationException → LikeException(LIKE_DUPLICATE)")
        void fail_duplicateLike() {
            // given
            given(likeRepository.findByUserIdAndAuctionId(1L, 1L)).willReturn(Optional.empty());
            org.hibernate.exception.ConstraintViolationException hibernateCve =
                    new org.hibernate.exception.ConstraintViolationException(
                            "Duplicate entry", new java.sql.SQLException(), "idx_likes_user_auction");
            DataIntegrityViolationException dive = new DataIntegrityViolationException("constraint", hibernateCve);
            given(likeRepository.save(any(Like.class))).willThrow(dive);

            // when & then
            assertThatThrownBy(() -> likeCommandService.toggleLike(1L, 1L))
                    .isInstanceOf(LikeException.class)
                    .satisfies(ex -> assertThat(((LikeException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.LIKE_DUPLICATE));
        }
    }

    // ---------------------------------------------------------------
    // T6: 비unique DataIntegrity 위반 (A3 구현 후 GREEN)
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("비unique DataIntegrity 위반")
    class NonUniqueDataIntegrityViolation {

        @Test
        @DisplayName("실패: save() 시 비unique DataIntegrityViolationException → 그대로 전파 (A3 구현 후 GREEN)")
        void fail_nonUniqueDataIntegrityViolation() {
            // given
            given(likeRepository.findByUserIdAndAuctionId(1L, 1L)).willReturn(Optional.empty());
            DataIntegrityViolationException dive =
                    new DataIntegrityViolationException("other constraint", new RuntimeException("not a constraint violation"));
            given(likeRepository.save(any(Like.class))).willThrow(dive);

            // when & then
            assertThatThrownBy(() -> likeCommandService.toggleLike(1L, 1L))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
