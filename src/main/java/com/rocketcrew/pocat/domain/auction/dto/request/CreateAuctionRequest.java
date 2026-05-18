package com.rocketcrew.pocat.domain.auction.dto.request;

import java.time.LocalDateTime;

public record CreateAuctionRequest(
        Long cardId,
        String title,
        String description,
        String cardImageUrl,
        Long startingPrice,
        Long buyoutPrice,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {
}
