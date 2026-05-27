package com.rocketcrew.pocat.domain.community.freepost.service;

import com.rocketcrew.pocat.domain.community.freepost.dto.response.FreePostResponse;
import com.rocketcrew.pocat.domain.community.freepost.entity.FreePost;
import com.rocketcrew.pocat.domain.community.freepost.repository.FreePostRepository;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.enums.UserRole;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.FreePostException;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FreePostQueryService")
class FreePostQueryServiceTest {

    @InjectMocks
    private FreePostQueryService freePostQueryService;

    @Mock
    private FreePostRepository freePostRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FreePostViewCountService viewCountService;

    private User user;
    private FreePost freePost;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
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
                .viewCount(5)
                .build();
        ReflectionTestUtils.setField(freePost, "id", 10L);

        pageable = PageRequest.of(0, 10);
    }

    @Nested
    @DisplayName("getPosts()")
    class GetPosts {

        @Test
        @DisplayName("성공: 키워드 포함 검색")
        void success_withKeyword() {
            Page<FreePost> postPage = new PageImpl<>(List.of(freePost), pageable, 1);
            given(freePostRepository.searchPosts("제목", pageable)).willReturn(postPage);
            given(userRepository.findAllById(List.of(1L))).willReturn(List.of(user));

            Page<FreePostResponse> result = freePostQueryService.getPosts("제목", pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).title()).isEqualTo("제목");
            assertThat(result.getContent().get(0).authorNickname()).isEqualTo("테스터");
        }

        @Test
        @DisplayName("성공: 키워드 없이 전체 조회")
        void success_withoutKeyword() {
            Page<FreePost> postPage = new PageImpl<>(List.of(freePost), pageable, 1);
            given(freePostRepository.searchPosts(null, pageable)).willReturn(postPage);
            given(userRepository.findAllById(List.of(1L))).willReturn(List.of(user));

            Page<FreePostResponse> result = freePostQueryService.getPosts(null, pageable);

            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getPost()")
    class GetPost {

        @Test
        @DisplayName("성공: 작성자가 아닌 경우 조회수 증가")
        void success_viewCountIncremented_forNonOwner() {
            given(freePostRepository.findById(10L)).willReturn(Optional.of(freePost));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            FreePostResponse response = freePostQueryService.getPost(10L, "127.0.0.1", 99L);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(10L);
            verify(viewCountService).increaseViewCount(10L, "127.0.0.1");
        }

        @Test
        @DisplayName("성공: 작성자인 경우 조회수 증가 없음")
        void success_noViewCountForOwner() {
            given(freePostRepository.findById(10L)).willReturn(Optional.of(freePost));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            FreePostResponse response = freePostQueryService.getPost(10L, "127.0.0.1", 1L);

            assertThat(response).isNotNull();
            verify(viewCountService, never()).increaseViewCount(anyLong(), anyString());
        }

        @Test
        @DisplayName("실패: 게시글 없음")
        void fail_postNotFound() {
            given(freePostRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> freePostQueryService.getPost(999L, "127.0.0.1", 1L))
                    .isInstanceOf(FreePostException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FREE_POST_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getMyPosts()")
    class GetMyPosts {

        @Test
        @DisplayName("성공: 내 게시글 목록 조회")
        void success() {
            Page<FreePost> postPage = new PageImpl<>(List.of(freePost), pageable, 1);
            given(freePostRepository.findByUserId(1L, pageable)).willReturn(postPage);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            Page<FreePostResponse> result = freePostQueryService.getMyPosts(1L, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).authorNickname()).isEqualTo("테스터");
        }
    }
}
