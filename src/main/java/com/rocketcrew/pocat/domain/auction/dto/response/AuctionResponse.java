package com.rocketcrew.pocat.domain.auction.dto.response;

import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;

import java.time.LocalDateTime;

public record AuctionResponse(
        Long id,
        Long cardId,
        Long sellerId,
        Long highestBidderId,
        String title,
        String description,
        String cardImageUrl,
        Long startingPrice,
        Long buyoutPrice,
        Long highestPrice,
        AuctionStatus status,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        String cancelReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AuctionResponse from(Auction auction) {
        return new AuctionResponse(
                auction.getId(),
                auction.getCardId(),
                auction.getSellerId(),
                auction.getHighestBidderId(),
                auction.getTitle(),
                auction.getDescription(),
                auction.getCardImageUrl(),
                auction.getStartingPrice(),
                auction.getBuyoutPrice(),
                auction.getHighestPrice(),
                auction.getStatus(),
                auction.getStartedAt(),
                auction.getEndedAt(),
                auction.getCancelReason(),
                auction.getCreatedAt(),
                auction.getUpdatedAt()
        );
    }
}
