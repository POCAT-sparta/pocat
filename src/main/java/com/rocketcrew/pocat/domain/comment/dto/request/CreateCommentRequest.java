package com.rocketcrew.pocat.domain.comment.dto.request;

public record CreateCommentRequest(
        Long postId,
        Long parentId,
        String content
) {
}
