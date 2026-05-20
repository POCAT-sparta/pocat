package com.rocketcrew.pocat.domain.bid.dto.response;

import java.time.LocalDateTime;

public record AuctionBidHistoryResponse(
        Long bidId,
        Long bidderId,
        String bidderNickname,
        Long bidPrice,
        LocalDateTime createdAt
) {
}
