package com.rocketcrew.pocat.domain.comment.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateCommentRequest(
        @NotBlank(message = "댓글 내용을 입력해 주세요.") String content
) {
}
