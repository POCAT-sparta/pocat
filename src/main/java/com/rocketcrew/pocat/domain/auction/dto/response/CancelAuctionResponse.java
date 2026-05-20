package com.rocketcrew.pocat.domain.auction.dto.response;

import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;

public record CancelAuctionResponse(
        Long auctionId,
        AuctionStatus status
) {
    public static CancelAuctionResponse from(Auction auction) {
        return new CancelAuctionResponse(
                auction.getId(),
                auction.getStatus()
        );
    }
}
