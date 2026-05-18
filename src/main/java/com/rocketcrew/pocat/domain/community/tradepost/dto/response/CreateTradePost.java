package com.rocketcrew.pocat.domain.community.tradepost.dto.response;

import com.rocketcrew.pocat.domain.community.tradepost.entity.TradePost;

public record CreateTradePost(
        Long id,
        String title
) {
    public static CreateTradePost from(TradePost tradePost) {
        return new CreateTradePost(tradePost.getId(), tradePost.getTitle());
    }
}
