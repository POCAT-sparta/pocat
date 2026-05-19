package com.rocketcrew.pocat.domain.auction.dto.response;

import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;

public record UpdateAuctionResponse(
        Long auctionId,
        String title,
        String description,
        Long startingPrice,
        Long buyoutPrice,
        AuctionStatus status
) {
    public static UpdateAuctionResponse from(Auction auction) {
        return new UpdateAuctionResponse(
                auction.getId(),
                auction.getTitle(),
                auction.getDescription(),
                auction.getStartingPrice(),
                auction.getBuyoutPrice(),
                auction.getStatus()
        );
    }
}
