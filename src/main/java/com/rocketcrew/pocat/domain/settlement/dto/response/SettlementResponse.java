package com.rocketcrew.pocat.domain.settlement.dto.response;

import com.rocketcrew.pocat.domain.settlement.entity.Settlement;
import com.rocketcrew.pocat.domain.settlement.entity.SettlementStatus;

import java.time.LocalDateTime;

public record SettlementResponse(
        Long id,
        Long orderId,
        Long sellerId,
        Long totalPrice,
        Long platformFee,
        Long sellerAmount,
        SettlementStatus status,
        LocalDateTime settledAt,
        LocalDateTime createdAt
) {
    public static SettlementResponse from(Settlement settlement) {
        return new SettlementResponse(
                settlement.getId(),
                settlement.getOrderId(),
                settlement.getSellerId(),
                settlement.getTotalPrice(),
                settlement.getPlatformFee(),
                settlement.getSellerAmount(),
                settlement.getStatus(),
                settlement.getSettledAt(),
                settlement.getCreatedAt()
        );
    }
}
