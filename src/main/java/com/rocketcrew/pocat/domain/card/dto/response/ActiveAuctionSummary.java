package com.rocketcrew.pocat.domain.card.dto.response;

import com.rocketcrew.pocat.domain.auction.entity.Auction;

import java.time.LocalDateTime;

public record ActiveAuctionSummary(
        Long auctionId,
        String title,
        Long startingPrice,
        Long buyoutPrice,
        Long highestPrice,
        LocalDateTime startedAt,
        LocalDateTime endedAt
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
