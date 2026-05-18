package com.rocketcrew.pocat.domain.community.tradepost.dto.request;

public record UpdateTradePostRequest(
        String title,
        String content,
        Long price,
        String thumbnail
) {
}
