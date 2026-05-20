package com.rocketcrew.pocat.domain.comment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCommentRequest(
        @NotNull(message = "게시글 ID는 필수입니다.") Long freePostId,
        Long parentId,
        @NotBlank(message = "댓글 내용을 입력해 주세요.") String content
) {
}
