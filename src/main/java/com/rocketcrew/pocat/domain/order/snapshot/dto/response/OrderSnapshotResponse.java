package com.rocketcrew.pocat.domain.order.snapshot.dto.response;

import com.rocketcrew.pocat.domain.order.snapshot.entity.OrderSnapshot;

import java.time.LocalDateTime;

public record OrderSnapshotResponse(
        Long id,
        String orderUid,
        Long finalPrice,
        Long feeRate,
        Long fee,
        Long sellerAmount,
        String snapshotJson,
        LocalDateTime createdAt
) {
    public static OrderSnapshotResponse from(OrderSnapshot snapshot) {
        return new OrderSnapshotResponse(
                snapshot.getId(),
                snapshot.getOrderUid(),
                snapshot.getFinalPrice(),
                snapshot.getFeeRate(),
                snapshot.getFee(),
                snapshot.getSellerAmount(),
                snapshot.getSnapshotJson(),
                snapshot.getCreatedAt()
        );
    }
}
