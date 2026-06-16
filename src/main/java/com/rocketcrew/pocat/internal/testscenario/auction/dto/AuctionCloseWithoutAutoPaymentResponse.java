package com.rocketcrew.pocat.internal.testscenario.auction.dto;

import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;

public record AuctionCloseWithoutAutoPaymentResponse(
        Long auctionId,
        AuctionStatus auctionStatus,
        Long winnerId,
        Long finalPrice,
        String orderUid,
        OrderStatus orderStatus,
        boolean auctionEndedEventPublished,
        boolean orderCreatedEventPublished,
        boolean autoPaymentSuppressed,
        String nextStep
) {
}
