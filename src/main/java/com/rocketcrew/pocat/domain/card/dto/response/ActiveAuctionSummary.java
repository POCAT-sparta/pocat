package com.rocketcrew.pocat.domain.card.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.global.time.KoreaTimeSerializer;

import java.time.LocalDateTime;

public record ActiveAuctionSummary(
        Long auctionId,
        String title,
        Long startingPrice,
        Long buyoutPrice,
        Long highestPrice,
        @JsonSerialize(using = KoreaTimeSerializer.class) LocalDateTime startedAt,
        @JsonSerialize(using = KoreaTimeSerializer.class) LocalDateTime endedAt
) {
    public static ActiveAuctionSummary from(Auction auction) {
        return new ActiveAuctionSummary(
                auction.getId(),
                auction.getTitle(),
                auction.getStartingPrice(),
                auction.getBuyoutPrice(),
                auction.getHighestPrice(),
                auction.getStartedAt(),
                auction.getEndedAt()
        );
    }
}
