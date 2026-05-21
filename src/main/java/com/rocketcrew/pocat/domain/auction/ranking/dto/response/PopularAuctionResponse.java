package com.rocketcrew.pocat.domain.auction.ranking.dto.response;

import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;

import java.time.LocalDateTime;

public record PopularAuctionResponse(
        Long auctionId,
        String title,
        Long cardId,
        String cardImageUrl,
        Long startingPrice,
        Long highestPrice,
        Long buyoutPrice,
        AuctionStatus status,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        long likeCount,
        long bidCount,
        double popularityScore
) {
    public static PopularAuctionResponse of(Auction auction, long likeCount, long bidCount, double score) {
        return new PopularAuctionResponse(
                auction.getId(),
                auction.getTitle(),
                auction.getCardId(),
                auction.getCardImageUrl(),
                auction.getStartingPrice(),
                auction.getHighestPrice(),
                auction.getBuyoutPrice(),
                auction.getStatus(),
                auction.getStartedAt(),
                auction.getEndedAt(),
                likeCount,
                bidCount,
                score
        );
    }
}
