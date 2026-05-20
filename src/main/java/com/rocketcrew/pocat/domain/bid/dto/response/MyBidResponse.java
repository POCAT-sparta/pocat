package com.rocketcrew.pocat.domain.bid.dto.response;

import com.rocketcrew.pocat.domain.bid.enums.BidStatus;

import java.time.LocalDateTime;

public record MyBidResponse(
        Long bidId,
        Long auctionId,
        String auctionTitle,
        Long bidPrice,
        BidStatus status,
        LocalDateTime createdAt
) {
}
