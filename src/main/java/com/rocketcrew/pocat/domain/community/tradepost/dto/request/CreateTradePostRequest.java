package com.rocketcrew.pocat.domain.community.tradepost.dto.request;

public record CreateTradePostRequest(
        String title,
        String content,
        Long price,
        String thumbnail
) {
}
