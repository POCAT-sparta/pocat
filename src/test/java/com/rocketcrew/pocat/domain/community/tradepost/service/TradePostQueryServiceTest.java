package com.rocketcrew.pocat.domain.community.tradepost.service;

import com.rocketcrew.pocat.domain.community.tradepost.dto.response.TradePostListResponse;
import com.rocketcrew.pocat.domain.community.tradepost.dto.response.TradePostResponse;
import com.rocketcrew.pocat.domain.community.tradepost.entity.TradePost;
import com.rocketcrew.pocat.domain.community.tradepost.repository.TradePostRepository;
import com.rocketcrew.pocat.domain.user.dto.response.UserResponse;
import com.rocketcrew.pocat.domain.user.enums.UserRole;
import com.rocketcrew.pocat.domain.user.service.UserQueryService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.TradePostException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TradePostQueryService")
class TradePostQueryServiceTest {

    @InjectMocks
    private TradePostQueryService tradePostQueryService;

    @Mock
    private TradePostRepository tradePostRepository;

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private ViewCountService viewCountService;

    @Mock
    private TradePostDetailCacheService tradePostDetailCacheService;

    private TradePost tradePost;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        tradePost = TradePost.builder()
                .userId(1L)
                .title("거래 게시글")
                .content("내용")
                .price(10000L)
                .thumbnail("thumb.jpg")
                .viewCount(0)
                .build();
        ReflectionTestUtils.setField(tradePost, "id", 10L);

        userResponse = new UserResponse(
                1L, "user@test.com", "테스터", "010-1234-5678",
                UserRole.USER, null, null, null,
                0, false, false, LocalDateTime.now()
        );
    }

    @Nested
    @DisplayName("getPosts()")
    class GetPosts {

        @Test
        @DisplayName("성공: 닉네임 배치 매핑하여 목록 반환")
        void success() {
            Pageable pageable = PageRequest.of(0, 20);
            given(tradePostRepository.searchPosts(any(), any(), any(), eq(pageable)))
                    .willReturn(new PageImpl<>(List.of(tradePost)));
            given(userQueryService.getNicknamesByUserIds(anyList()))
                    .willReturn(Map.of(1L, "테스터"));

            Page<TradePostListResponse> result = tradePostQueryService.getPosts(null, null, null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).authorNickname()).isEqualTo("테스터");
        }
    }

    @Nested
    @DisplayName("getPostsByUserId()")
    class GetPostsByUserId {

        @Test
        @DisplayName("성공: 특정 유저의 거래 게시글 목록 반환")
        void success() {
            Pageable pageable = PageRequest.of(0, 20);
            given(userQueryService.getUserById(1L)).willReturn(userResponse);
            given(tradePostRepository.findByUserId(1L, pageable))
                    .willReturn(new PageImpl<>(List.of(tradePost)));

            Page<TradePostListResponse> result = tradePostQueryService.getPostsByUserId(1L, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).title()).isEqualTo("거래 게시글");
        }
    }

    @Nested
    @DisplayName("getPost()")
    class GetPost {

        @Test
        @DisplayName("성공: 비소유자 조회 → viewCountService.increaseViewCount() 호출")
        void nonOwnerView_increasesViewCount() {
            given(tradePostRepository.findById(10L)).willReturn(Optional.of(tradePost));
            TradePostResponse detail = TradePostResponse.from(tradePost, "테스터");
            given(tradePostDetailCacheService.loadPostDetail(10L)).willReturn(detail);

            // requesterId=99 != post.getUserId()=1 → view count should be increased
            TradePostResponse result = tradePostQueryService.getPost(10L, "127.0.0.1", 99L);

            assertThat(result).isNotNull();
            assertThat(result.title()).isEqualTo("거래 게시글");
            assertThat(result.price()).isEqualTo(10000L);
            assertThat(result.authorNickname()).isEqualTo("테스터");
            verify(viewCountService).increaseViewCount(10L, "127.0.0.1");
        }

        @Test
        @DisplayName("성공: 소유자 조회 → viewCountService.increaseViewCount() 미호출")
        void ownerView_doesNotIncreaseViewCount() {
            given(tradePostRepository.findById(10L)).willReturn(Optional.of(tradePost));
            TradePostResponse detail = TradePostResponse.from(tradePost, "테스터");
            given(tradePostDetailCacheService.loadPostDetail(10L)).willReturn(detail);

            // requesterId=1 == post.getUserId()=1 → view count should NOT be increased
            TradePostResponse result = tradePostQueryService.getPost(10L, "127.0.0.1", 1L);

            assertThat(result).isNotNull();
            verify(viewCountService, never()).increaseViewCount(any(), any());
        }

        @Test
        @DisplayName("실패: 게시글 없음 - TRADE_POST_NOT_FOUND")
        void notFound() {
            given(tradePostRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> tradePostQueryService.getPost(999L, "127.0.0.1", 1L))
                    .isInstanceOf(TradePostException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRADE_POST_NOT_FOUND);
        }
    }
}
