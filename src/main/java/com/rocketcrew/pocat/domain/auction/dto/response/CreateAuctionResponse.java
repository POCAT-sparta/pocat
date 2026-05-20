package com.rocketcrew.pocat.domain.auction.dto.response;

import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;

public record CreateAuctionResponse(
        Long auctionId,
        String title,
        AuctionStatus status
) {
    public static CreateAuctionResponse from(Auction auction) {
        return new CreateAuctionResponse(
                auction.getId(),
                auction.getTitle(),
                auction.getStatus()
        );
    }
}
