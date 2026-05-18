package com.rocketcrew.pocat.domain.bid.dto.response;

import com.rocketcrew.pocat.domain.bid.entity.AuctionBid;
import com.rocketcrew.pocat.domain.bid.enums.BidStatus;

import java.time.LocalDateTime;

public record AuctionBidResponse(
        Long id,
        Long userId,
        Long auctionId,
        Long bidPrice,
        BidStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AuctionBidResponse from(AuctionBid auctionBid) {
        return new AuctionBidResponse(
                auctionBid.getId(),
                auctionBid.getUserId(),
                auctionBid.getAuctionId(),
                auctionBid.getBidPrice(),
                auctionBid.getStatus(),
                auctionBid.getCreatedAt(),
                auctionBid.getUpdatedAt()
        );
    }
}
