package com.rocketcrew.pocat.domain.community.freepost.service;

import com.rocketcrew.pocat.domain.community.freepost.cache.PostCommentCacheEvictor;
import com.rocketcrew.pocat.domain.community.freepost.dto.request.CreateFreePostRequest;
import com.rocketcrew.pocat.domain.community.freepost.dto.request.UpdateFreePostRequest;
import com.rocketcrew.pocat.domain.community.freepost.dto.response.FreePostResponse;
import com.rocketcrew.pocat.domain.community.freepost.entity.FreePost;
import com.rocketcrew.pocat.domain.community.freepost.repository.FreePostRepository;
import com.rocketcrew.pocat.domain.community.freepost.service.FreePostDetailCacheService;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.enums.UserRole;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.FreePostException;
import com.rocketcrew.pocat.global.exception.domain.UserException;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FreePostCommandService")
class FreePostCommandServiceTest {

    @InjectMocks
    private FreePostCommandService freePostCommandService;

    @Mock
    private FreePostRepository freePostRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PostCommentCacheEvictor postCommentCacheEvictor;

    @Mock
    private FreePostDetailCacheService freePostDetailCacheService;

    @Mock
    private RedisRateLimiter redisRateLimiter;

    @Mock
    private RateLimitProperties rateLimitProperties;

    private User user;
    private FreePost freePost;

    @BeforeEach
    void setUp() {
        given(redisRateLimiter.isAllowed(anyString(), anyInt(), anyLong())).willReturn(true);

        user = User.builder()
                .email("user@test.com")
                .password("pw")
                .nickname("테스터")
                .userRole(UserRole.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        freePost = FreePost.builder()
                .userId(1L)
                .title("제목")
                .content("내용")
                .viewCount(0)
                .build();
        ReflectionTestUtils.setField(freePost, "id", 10L);
    }

    @Nested
    @DisplayName("createPost()")
    class CreatePost {

        @Test
        @DisplayName("성공: 게시글 생성")
        void success() {
            CreateFreePostRequest request = new CreateFreePostRequest("새 제목", "새 내용");
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(freePostRepository.save(any(FreePost.class))).willReturn(freePost);

            FreePostResponse response = freePostCommandService.createPost(1L, request);

            assertThat(response).isNotNull();
            assertThat(response.authorNickname()).isEqualTo("테스터");
            verify(freePostRepository).save(any(FreePost.class));
        }

        @Test
        @DisplayName("실패: 유저 없음")
        void fail_userNotFound() {
            CreateFreePostRequest request = new CreateFreePostRequest("제목", "내용");
            given(userRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> freePostCommandService.createPost(99L, request))
                    .isInstanceOf(UserException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("updatePost()")
    class UpdatePost {

        @Test
        @DisplayName("성공: 게시글 수정")
        void success() {
            UpdateFreePostRequest request = new UpdateFreePostRequest("수정 제목", "수정 내용");
            given(freePostRepository.findById(10L)).willReturn(Optional.of(freePost));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            FreePostResponse response = freePostCommandService.updatePost(10L, 1L, request);

            assertThat(response).isNotNull();
            assertThat(response.title()).isEqualTo("수정 제목");
        }

        @Test
        @DisplayName("실패: 게시글 없음")
        void fail_postNotFound() {
            UpdateFreePostRequest request = new UpdateFreePostRequest("제목", "내용");
            given(freePostRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> freePostCommandService.updatePost(999L, 1L, request))
                    .isInstanceOf(FreePostException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FREE_POST_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 소유자가 아님 (USER_FORBIDDEN)")
        void fail_userForbidden() {
            UpdateFreePostRequest request = new UpdateFreePostRequest("제목", "내용");
            given(freePostRepository.findById(10L)).willReturn(Optional.of(freePost));

            assertThatThrownBy(() -> freePostCommandService.updatePost(10L, 99L, request))
                    .isInstanceOf(FreePostException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_FORBIDDEN);
        }

        @Test
        @DisplayName("실패: 빈 제목 (INVALID_CONTENT)")
        void fail_blankTitle() {
            UpdateFreePostRequest request = new UpdateFreePostRequest("   ", "내용");
            given(freePostRepository.findById(10L)).willReturn(Optional.of(freePost));

            assertThatThrownBy(() -> freePostCommandService.updatePost(10L, 1L, request))
                    .isInstanceOf(FreePostException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CONTENT);
        }
    }

    @Nested
    @DisplayName("deletePost()")
    class DeletePost {

        @Test
        @DisplayName("성공: 게시글 삭제")
        void success() {
            given(freePostRepository.findById(10L)).willReturn(Optional.of(freePost));

            freePostCommandService.deletePost(10L, 1L);

            verify(freePostRepository).delete(freePost);
        }

        @Test
        @DisplayName("실패: 게시글 없음")
        void fail_postNotFound() {
            given(freePostRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> freePostCommandService.deletePost(999L, 1L))
                    .isInstanceOf(FreePostException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FREE_POST_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 소유자가 아님 (USER_FORBIDDEN)")
        void fail_userForbidden() {
            given(freePostRepository.findById(10L)).willReturn(Optional.of(freePost));

            assertThatThrownBy(() -> freePostCommandService.deletePost(10L, 99L))
                    .isInstanceOf(FreePostException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_FORBIDDEN);
        }
    }
}
