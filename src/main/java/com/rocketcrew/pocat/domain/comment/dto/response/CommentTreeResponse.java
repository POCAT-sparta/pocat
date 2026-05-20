package com.rocketcrew.pocat.domain.comment.dto.response;

import com.rocketcrew.pocat.domain.comment.entity.Comment;
import java.time.LocalDateTime;
import java.util.List;

public record CommentTreeResponse(
        Long id,
        Long freePostId,
        Long parentId,
        Long authorId,
        String authorNickname,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<CommentTreeResponse> children
) {
    public static CommentTreeResponse of(Comment comment, String authorNickname, List<CommentTreeResponse> children) {
        return new CommentTreeResponse(
                comment.getId(),
                comment.getFreePostId(),
                comment.getParentId(),
                comment.getUserId(),
                authorNickname,
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                children
        );
    }

    public static CommentTreeResponse of(Comment comment, String authorNickname) {
        return of(comment, authorNickname, List.of());
    }
}
