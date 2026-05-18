package com.rocketcrew.pocat.domain.community.tradepost.dto.response;

import com.rocketcrew.pocat.domain.community.tradepost.entity.TradePost;

public record UpdateTradePostResponse(
        Long id,
        String title,
        Long price
) {
    public static UpdateTradePostResponse from(TradePost tradePost) {
        return new UpdateTradePostResponse(tradePost.getId(), tradePost.getTitle(), tradePost.getPrice());
    }
}
