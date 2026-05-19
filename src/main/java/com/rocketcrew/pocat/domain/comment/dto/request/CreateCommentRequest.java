package com.rocketcrew.pocat.domain.comment.dto.request;

public record CreateCommentRequest(
        Long freePostId,
        Long parentId,
        String content
) {
}
