package com.rocketcrew.pocat.domain.community.tradepost.service;

import com.rocketcrew.pocat.domain.ai.rag.event.TradePostEmbeddingEvent;
import com.rocketcrew.pocat.domain.community.tradepost.dto.request.CreateTradePostRequest;
import com.rocketcrew.pocat.domain.community.tradepost.dto.request.UpdateTradePostRequest;
import com.rocketcrew.pocat.domain.community.tradepost.dto.response.CreateTradePost;
import com.rocketcrew.pocat.domain.community.tradepost.dto.response.UpdateTradePostResponse;
import com.rocketcrew.pocat.domain.community.tradepost.entity.TradePost;
import com.rocketcrew.pocat.domain.community.tradepost.repository.TradePostRepository;
import com.rocketcrew.pocat.domain.user.enums.UserRole;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;
import com.rocketcrew.pocat.global.exception.domain.TradePostException;
import com.rocketcrew.pocat.global.filter.BadWordFilterService;
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
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TradePostCommandService")
class TradePostCommandServiceTest {

    @InjectMocks
    private TradePostCommandService tradePostCommandService;

    @Mock
    private TradePostRepository tradePostRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private RedisRateLimiter redisRateLimiter;

    @Mock
    private RateLimitProperties rateLimitProperties;

    @Mock
    private BadWordFilterService badWordFilterService;

    private TradePost tradePost;

    @BeforeEach
    void setUp() {
        given(redisRateLimiter.isAllowed(anyString(), anyInt(), anyLong())).willReturn(true);
        willDoNothing().given(badWordFilterService).validate(any());

        tradePost = TradePost.builder()
                .userId(1L)
                .title("거래 게시글")
                .content("내용")
                .price(10000L)
                .thumbnail("thumb.jpg")
                .viewCount(0)
                .build();
        ReflectionTestUtils.setField(tradePost, "id", 10L);
    }

    @Nested
    @DisplayName("createPost()")
    class CreatePost {

        @Test
        @DisplayName("성공: 거래 게시글 생성 및 임베딩 이벤트 발행")
        void success() {
            CreateTradePostRequest request = new CreateTradePostRequest(
                    "거래 게시글", "내용", 10000L, "thumb.jpg");
            given(tradePostRepository.save(any(TradePost.class))).willReturn(tradePost);

            CreateTradePost response = tradePostCommandService.createPost(1L, request);

            assertThat(response).isNotNull();
            assertThat(response.title()).isEqualTo("거래 게시글");
            verify(tradePostRepository).save(any(TradePost.class));

            ArgumentCaptor<TradePostEmbeddingEvent> eventCaptor = ArgumentCaptor.forClass(TradePostEmbeddingEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            TradePostEmbeddingEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.postId()).isEqualTo(10L);
            assertThat(capturedEvent.content()).contains("내용");
        }

        @Test
        @DisplayName("실패: 제목 또는 내용에 금지어 포함 → ServiceException(CONTAINS_BAD_WORD)")
        void containsBadWord_throwsException() {
            CreateTradePostRequest request = new CreateTradePostRequest(
                    "욕설 제목", "정상 내용", 10000L, "thumb.jpg");
            willThrow(new ServiceException(ErrorCode.CONTAINS_BAD_WORD))
                    .given(badWordFilterService).validate(request.title(), request.content());

            assertThatThrownBy(() -> tradePostCommandService.createPost(1L, request))
                    .isInstanceOf(ServiceException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTAINS_BAD_WORD);
        }
    }

    @Nested
    @DisplayName("updatePost()")
    class UpdatePost {

        @Test
        @DisplayName("성공: 거래 게시글 수정 및 임베딩 이벤트 발행")
        void success() {
            UpdateTradePostRequest request = new UpdateTradePostRequest(
                    "수정된 제목", "수정된 내용", 20000L, "thumb2.jpg");
            given(tradePostRepository.findById(10L)).willReturn(Optional.of(tradePost));

            UpdateTradePostResponse response = tradePostCommandService.updatePost(10L, 1L, request);

            assertThat(response).isNotNull();
            ArgumentCaptor<TradePostEmbeddingEvent> eventCaptor = ArgumentCaptor.forClass(TradePostEmbeddingEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().postId()).isEqualTo(10L);
            assertThat(eventCaptor.getValue().content()).isEqualTo("수정된 내용");
        }

        @Test
        @DisplayName("실패: 게시글 없음 - TRADE_POST_NOT_FOUND")
        void notFound() {
            UpdateTradePostRequest request = new UpdateTradePostRequest(
                    "수정", "내용", 5000L, "thumb.jpg");
            given(tradePostRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> tradePostCommandService.updatePost(999L, 1L, request))
                    .isInstanceOf(TradePostException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRADE_POST_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 소유자 불일치 - USER_FORBIDDEN")
        void userForbidden() {
            UpdateTradePostRequest request = new UpdateTradePostRequest(
                    "수정", "내용", 5000L, "thumb.jpg");
            given(tradePostRepository.findById(10L)).willReturn(Optional.of(tradePost));

            assertThatThrownBy(() -> tradePostCommandService.updatePost(10L, 99L, request))
                    .isInstanceOf(TradePostException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_FORBIDDEN);
        }

        @Test
        @DisplayName("실패: 제목 또는 내용에 금지어 포함 → ServiceException(CONTAINS_BAD_WORD)")
        void containsBadWord_throwsException() {
            UpdateTradePostRequest request = new UpdateTradePostRequest(
                    "욕설 제목", "정상 내용", 20000L, "thumb.jpg");
            given(tradePostRepository.findById(10L)).willReturn(Optional.of(tradePost));
            willThrow(new ServiceException(ErrorCode.CONTAINS_BAD_WORD))
                    .given(badWordFilterService).validate(request.title(), request.content());

            assertThatThrownBy(() -> tradePostCommandService.updatePost(10L, 1L, request))
                    .isInstanceOf(ServiceException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTAINS_BAD_WORD);
        }
    }

    @Nested
    @DisplayName("deletePost()")
    class DeletePost {

        @Test
        @DisplayName("성공: 소유자 삭제 - 이벤트 미발행")
        void ownerDeletes() {
            given(tradePostRepository.findById(10L)).willReturn(Optional.of(tradePost));

            tradePostCommandService.deletePost(10L, 1L, UserRole.USER.name());

            verify(tradePostRepository).delete(tradePost);
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("성공: ADMIN 삭제")
        void adminDeletes() {
            given(tradePostRepository.findById(10L)).willReturn(Optional.of(tradePost));

            // userId=99 is not owner, but role is ADMIN
            tradePostCommandService.deletePost(10L, 99L, UserRole.ADMIN.name());

            verify(tradePostRepository).delete(tradePost);
        }

        @Test
        @DisplayName("실패: 게시글 없음 - TRADE_POST_NOT_FOUND")
        void notFound() {
            given(tradePostRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> tradePostCommandService.deletePost(999L, 1L, UserRole.USER.name()))
                    .isInstanceOf(TradePostException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRADE_POST_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 소유자도 아니고 ADMIN도 아님 - USER_FORBIDDEN")
        void userForbidden() {
            given(tradePostRepository.findById(10L)).willReturn(Optional.of(tradePost));

            assertThatThrownBy(() -> tradePostCommandService.deletePost(10L, 99L, UserRole.USER.name()))
                    .isInstanceOf(TradePostException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_FORBIDDEN);
        }
    }
}
