package com.rocketcrew.pocat.domain.community.tradepost.dto.response;

import com.rocketcrew.pocat.domain.community.tradepost.entity.TradePost;

import java.time.LocalDateTime;

public record TradePostListResponse(
        Long id,
        String title,
        String authorNickname,
        Long price,
        String thumbnail,
        int viewCount,
        LocalDateTime createdAt
) {
    public static TradePostListResponse from(TradePost tradePost, String authorNickname) {
        return new TradePostListResponse(
                tradePost.getId(),
                tradePost.getTitle(),
                authorNickname,
                tradePost.getPrice(),
                tradePost.getThumbnail(),
                tradePost.getViewCount(),
                tradePost.getCreatedAt()
        );
    }
}
