package com.rocketcrew.pocat.domain.auction.snapshot.dto.response;

import com.rocketcrew.pocat.domain.auction.snapshot.entity.AuctionSnapshot;

import java.time.LocalDateTime;

public record AuctionSnapshotResponse(
        Long id,
        Long auctionId,
        Long finalPrice,
        String snapshotJson,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AuctionSnapshotResponse from(AuctionSnapshot snapshot) {
        return new AuctionSnapshotResponse(
                snapshot.getId(),
                snapshot.getAuctionId(),
                snapshot.getFinalPrice(),
                snapshot.getSnapshotJson(),
                snapshot.getCreatedAt(),
                snapshot.getUpdatedAt()
        );
    }
}
