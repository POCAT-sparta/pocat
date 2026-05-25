package com.rocketcrew.pocat.domain.auction.dto.response;

import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;

import java.time.LocalDateTime;

public record InspectAuctionResponse(
        Long auctionId,
        AuctionStatus status,
        String reason,
        LocalDateTime inspectedAt,
        Long inspectedBy
) {
    public static InspectAuctionResponse from(Auction auction) {
        return new InspectAuctionResponse(
                auction.getId(),
                auction.getStatus(),
                auction.getReason(),
                auction.getInspectedAt(),
                auction.getInspectedBy()
        );
    }
}
