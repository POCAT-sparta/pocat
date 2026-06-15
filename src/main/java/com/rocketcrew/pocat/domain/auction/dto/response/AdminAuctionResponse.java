package com.rocketcrew.pocat.domain.auction.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.global.time.KoreaTimeSerializer;

import java.time.LocalDateTime;

public record AdminAuctionResponse(
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
        @JsonSerialize(using = KoreaTimeSerializer.class) LocalDateTime startedAt,
        @JsonSerialize(using = KoreaTimeSerializer.class) LocalDateTime endedAt
) {
}
