package com.rocketcrew.pocat.domain.bid.dto.request;

public record CreateBidRequest(
        Long auctionId,
        Long bidPrice
) {
}
