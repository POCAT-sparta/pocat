package com.rocketcrew.pocat.domain.comment.service;

import com.rocketcrew.pocat.domain.comment.dto.request.CreateCommentRequest;
import com.rocketcrew.pocat.domain.comment.dto.request.UpdateCommentRequest;
import com.rocketcrew.pocat.domain.comment.dto.response.CommentResponse;
import com.rocketcrew.pocat.domain.comment.entity.Comment;
import com.rocketcrew.pocat.domain.comment.repository.CommentRepository;
import com.rocketcrew.pocat.domain.community.freepost.cache.PostCommentCacheEvictor;
import com.rocketcrew.pocat.domain.community.freepost.repository.FreePostRepository;
import com.rocketcrew.pocat.domain.community.freepost.service.FreePostCommentCountService;
import com.rocketcrew.pocat.domain.community.freepost.service.FreePostDetailCacheService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;
import com.rocketcrew.pocat.global.exception.domain.CommentException;
import com.rocketcrew.pocat.global.exception.domain.FreePostException;
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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CommentCommandService")
class CommentCommandServiceTest {

    @InjectMocks
    private CommentCommandService commentCommandService;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private FreePostRepository freePostRepository;

    @Mock
    private FreePostCommentCountService freePostCommentCountService;

    @Mock
    private PostCommentCacheEvictor postCommentCacheEvictor;

    @Mock
    private FreePostDetailCacheService freePostDetailCacheService;

    @Mock
    private RedisRateLimiter redisRateLimiter;

    @Mock
    private RateLimitProperties rateLimitProperties;

    @Mock
    private BadWordFilterService badWordFilterService;

    private Comment comment;

    @BeforeEach
    void setUp() {
        given(redisRateLimiter.isAllowed(anyString(), anyInt(), anyLong())).willReturn(true);
        willDoNothing().given(badWordFilterService).validate(any());

        comment = Comment.builder()
                .userId(1L)
                .freePostId(10L)
                .parentId(null)
                .content("정상 댓글 내용")
                .build();
        ReflectionTestUtils.setField(comment, "id", 100L);
    }

    @Nested
    @DisplayName("createComment()")
    class CreateComment {

        @Test
        @DisplayName("성공: 댓글 생성")
        void success() {
            CreateCommentRequest request = new CreateCommentRequest(10L, null, "정상 댓글 내용");
            given(freePostRepository.existsById(10L)).willReturn(true);
            given(commentRepository.save(any(Comment.class))).willReturn(comment);

            CommentResponse response = commentCommandService.createComment(1L, request);

            assertThat(response).isNotNull();
            assertThat(response.content()).isEqualTo("정상 댓글 내용");
            verify(commentRepository).save(any(Comment.class));
        }

        @Test
        @DisplayName("실패: Rate Limit 초과 - RATE_LIMIT_EXCEEDED")
        void fail_rateLimitExceeded() {
            CreateCommentRequest request = new CreateCommentRequest(10L, null, "내용");
            given(redisRateLimiter.isAllowed(anyString(), anyInt(), anyLong())).willReturn(false);

            assertThatThrownBy(() -> commentCommandService.createComment(1L, request))
                    .isInstanceOf(ServiceException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RATE_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("실패: 자유 게시글 없음 - FREE_POST_NOT_FOUND")
        void fail_freePostNotFound() {
            CreateCommentRequest request = new CreateCommentRequest(999L, null, "내용");
            given(freePostRepository.existsById(999L)).willReturn(false);

            assertThatThrownBy(() -> commentCommandService.createComment(1L, request))
                    .isInstanceOf(FreePostException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FREE_POST_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 금지어 포함 - CONTAINS_BAD_WORD")
        void fail_containsBadWord() {
            CreateCommentRequest request = new CreateCommentRequest(10L, null, "욕설이 포함된 내용");
            willThrow(new ServiceException(ErrorCode.CONTAINS_BAD_WORD))
                    .given(badWordFilterService).validate(request.content());

            assertThatThrownBy(() -> commentCommandService.createComment(1L, request))
                    .isInstanceOf(ServiceException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTAINS_BAD_WORD);
        }
    }

    @Nested
    @DisplayName("updateComment()")
    class UpdateComment {

        @Test
        @DisplayName("성공: 댓글 수정")
        void success() {
            UpdateCommentRequest request = new UpdateCommentRequest("수정된 내용");
            given(commentRepository.findById(100L)).willReturn(Optional.of(comment));

            CommentResponse response = commentCommandService.updateComment(100L, 1L, request);

            assertThat(response).isNotNull();
            assertThat(response.content()).isEqualTo("수정된 내용");
        }

        @Test
        @DisplayName("실패: 댓글 없음 - COMMENT_NOT_FOUND")
        void fail_commentNotFound() {
            UpdateCommentRequest request = new UpdateCommentRequest("수정된 내용");
            given(commentRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> commentCommandService.updateComment(999L, 1L, request))
                    .isInstanceOf(CommentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMENT_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 소유자가 아님 - USER_FORBIDDEN")
        void fail_userForbidden() {
            UpdateCommentRequest request = new UpdateCommentRequest("수정된 내용");
            given(commentRepository.findById(100L)).willReturn(Optional.of(comment));

            assertThatThrownBy(() -> commentCommandService.updateComment(100L, 99L, request))
                    .isInstanceOf(CommentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_FORBIDDEN);
        }

        @Test
        @DisplayName("실패: 금지어 포함 - CONTAINS_BAD_WORD")
        void fail_containsBadWord() {
            UpdateCommentRequest request = new UpdateCommentRequest("욕설이 포함된 내용");
            given(commentRepository.findById(100L)).willReturn(Optional.of(comment));
            willThrow(new ServiceException(ErrorCode.CONTAINS_BAD_WORD))
                    .given(badWordFilterService).validate(request.content());

            assertThatThrownBy(() -> commentCommandService.updateComment(100L, 1L, request))
                    .isInstanceOf(ServiceException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTAINS_BAD_WORD);
        }
    }

    @Nested
    @DisplayName("deleteComment()")
    class DeleteComment {

        @Test
        @DisplayName("성공: 댓글 삭제")
        void success() {
            given(commentRepository.findById(100L)).willReturn(Optional.of(comment));

            commentCommandService.deleteComment(100L, 1L);

            verify(commentRepository).delete(comment);
        }
    }
}
