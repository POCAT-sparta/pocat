package com.rocketcrew.pocat.domain.comment.dto.response;

import com.rocketcrew.pocat.domain.comment.entity.Comment;
import java.time.LocalDateTime;
import java.util.List;

public record CommentTreeResponse(
        Long id,
        Long postId,
        Long parentId,
        Long authorId,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<CommentTreeResponse> children
) {
    public static CommentTreeResponse from(Comment comment, List<CommentTreeResponse> children) {
        return new CommentTreeResponse(
                comment.getId(),
                comment.getPostId(),
                comment.getParentId(),
                comment.getUserId(),
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                children
        );
    }

    public static CommentTreeResponse from(Comment comment) {
        return from(comment, List.of());
    }
}
