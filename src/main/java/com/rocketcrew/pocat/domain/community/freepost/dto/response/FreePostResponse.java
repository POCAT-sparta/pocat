package com.rocketcrew.pocat.domain.community.freepost.dto.response;

import com.rocketcrew.pocat.domain.community.freepost.entity.FreePost;

import java.time.LocalDateTime;

public record FreePostResponse(
        Long id,
        Long userId,
        String title,
        String content,
        int viewCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static FreePostResponse from(FreePost freePost) {
        return new FreePostResponse(
                freePost.getId(),
                freePost.getUserId(),
                freePost.getTitle(),
                freePost.getContent(),
                freePost.getViewCount(),
                freePost.getCreatedAt(),
                freePost.getUpdatedAt()
        );
    }
}
