package com.rocketcrew.pocat.domain.auction.dto.response;

import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;

public record AdminCancelAuctionResponse(
        Long auctionId,
        AuctionStatus status,
        String reason
) {
    public static AdminCancelAuctionResponse from(Auction auction) {
        return new AdminCancelAuctionResponse(
                auction.getId(),
                auction.getStatus(),
                auction.getReason()
        );
    }
}
