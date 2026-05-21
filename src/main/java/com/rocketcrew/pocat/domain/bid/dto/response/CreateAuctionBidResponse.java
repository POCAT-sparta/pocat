package com.rocketcrew.pocat.domain.bid.dto.response;

import com.rocketcrew.pocat.domain.bid.entity.AuctionBid;
import com.rocketcrew.pocat.domain.bid.enums.BidStatus;

import java.time.LocalDateTime;

public record CreateAuctionBidResponse(
        Long id,
        Long userId,
        Long auctionId,
        Long bidPrice,
        BidStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CreateAuctionBidResponse from(AuctionBid auctionBid) {
        return new CreateAuctionBidResponse(
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
