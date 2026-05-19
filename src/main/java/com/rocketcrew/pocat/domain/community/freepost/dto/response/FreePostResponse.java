package com.rocketcrew.pocat.domain.community.freepost.dto.response;

import com.rocketcrew.pocat.domain.community.freepost.entity.FreePost;

import java.time.LocalDateTime;

public record FreePostResponse(
        Long id,
        Long authorId,
        String authorNickname,
        String title,
        String content,
        int viewCount,
        int commentCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static FreePostResponse of(FreePost freePost, String authorNickname, int commentCount) {
        return new FreePostResponse(
                freePost.getId(),
                freePost.getUserId(),
                authorNickname,
                freePost.getTitle(),
                freePost.getContent(),
                freePost.getViewCount(),
                commentCount,
                freePost.getCreatedAt(),
                freePost.getUpdatedAt()
        );
    }
}
