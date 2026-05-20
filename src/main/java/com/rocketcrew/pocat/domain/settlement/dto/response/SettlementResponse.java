package com.rocketcrew.pocat.domain.settlement.dto.response;

import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.settlement.entity.Settlement;
import com.rocketcrew.pocat.domain.settlement.enums.SettlementStatus;

import java.time.LocalDateTime;

public record SettlementResponse(
        String settlementUid,
        String orderUid,
        String cardName,
        CardGrade cardGrade,
        String cardImageUrl,
        Long totalPrice,
        Long platformFee,
        Long sellerAmount,
        SettlementStatus status,
        LocalDateTime settledAt,
        LocalDateTime createdAt
) {
    public static SettlementResponse from(Settlement settlement, Order order, Card card) {
        return new SettlementResponse(
                settlement.getSettlementUid(),
                order.getOrderUid(),
                card.getName(),
                card.getGrade(),
                card.getImageUrl(),
                settlement.getTotalPrice(),
                settlement.getPlatformFee(),
                settlement.getSellerAmount(),
                settlement.getStatus(),
                settlement.getSettledAt(),
                settlement.getCreatedAt()
        );
    }
}
