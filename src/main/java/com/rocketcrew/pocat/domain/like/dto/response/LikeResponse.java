package com.rocketcrew.pocat.domain.like.dto.response;

import com.rocketcrew.pocat.domain.like.entity.Like;

import java.time.LocalDateTime;

public record LikeResponse(
        Long id,
        Long userId,
        Long auctionId,
        LocalDateTime createdAt
) {
    public static LikeResponse from(Like like) {
        return new LikeResponse(
                like.getId(),
                like.getUserId(),
                like.getAuctionId(),
                like.getCreatedAt()
        );
    }
}
