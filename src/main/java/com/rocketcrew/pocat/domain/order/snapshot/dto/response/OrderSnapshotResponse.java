package com.rocketcrew.pocat.domain.order.snapshot.dto.response;

import com.rocketcrew.pocat.domain.order.snapshot.entity.OrderSnapshot;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderSnapshotResponse(
        Long id,
        Long orderId,
        BigDecimal finalPrice,
        BigDecimal feeRate,
        BigDecimal fee,
        BigDecimal sellerAmount,
        String snapshotJson,
        LocalDateTime createdAt
) {
    public static OrderSnapshotResponse from(OrderSnapshot snapshot) {
        return new OrderSnapshotResponse(
                snapshot.getId(),
                snapshot.getOrderId(),
                snapshot.getFinalPrice(),
                snapshot.getFeeRate(),
                snapshot.getFee(),
                snapshot.getSellerAmount(),
                snapshot.getSnapshotJson(),
                snapshot.getCreatedAt()
        );
    }
}
