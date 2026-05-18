package com.rocketcrew.pocat.domain.community.tradepost.dto.response;

import com.rocketcrew.pocat.domain.community.tradepost.entity.TradePost;

import java.time.LocalDateTime;

public record TradePostResponse(
        Long id,
        Long userId,
        String title,
        String content,
        Long price,
        String thumbnail,
        int viewCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static TradePostResponse from(TradePost tradePost) {
        return new TradePostResponse(
                tradePost.getId(),
                tradePost.getUserId(),
                tradePost.getTitle(),
                tradePost.getContent(),
                tradePost.getPrice(),
                tradePost.getThumbnail(),
                tradePost.getViewCount(),
                tradePost.getCreatedAt(),
                tradePost.getUpdatedAt()
        );
    }
}
