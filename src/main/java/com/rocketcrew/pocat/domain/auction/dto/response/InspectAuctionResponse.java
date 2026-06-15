package com.rocketcrew.pocat.domain.auction.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.global.time.KoreaTimeSerializer;

import java.time.LocalDateTime;

public record InspectAuctionResponse(
        Long auctionId,
        AuctionStatus status,
        String reason,
        @JsonSerialize(using = KoreaTimeSerializer.class) LocalDateTime inspectedAt,
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
