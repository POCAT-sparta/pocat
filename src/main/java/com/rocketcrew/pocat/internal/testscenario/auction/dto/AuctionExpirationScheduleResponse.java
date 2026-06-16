package com.rocketcrew.pocat.internal.testscenario.auction.dto;

import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;

import java.time.LocalDateTime;

public record AuctionExpirationScheduleResponse(
        Long auctionId,
        AuctionStatus status,
        LocalDateTime beforeEndedAt,
        LocalDateTime afterEndedAt,
        long ttlSeconds,
        boolean redisExpirationKeysScheduled,
        String expectedFlow
) {
}
