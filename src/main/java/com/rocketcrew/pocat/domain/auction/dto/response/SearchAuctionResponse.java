package com.rocketcrew.pocat.domain.auction.dto.response;

import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;

import java.time.LocalDateTime;

public record SearchAuctionResponse(
        Long auctionId,
        Long sellerId,
        String sellerNickname,
        String title,
        Long cardId,
        String cardName,
        CardGrade grade,
        String cardImageUrl,
        Long startingPrice,
        Long highestPrice,
        Long buyoutPrice,
        AuctionStatus status,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        LocalDateTime createdAt
) {
}
