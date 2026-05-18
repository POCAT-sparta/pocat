package com.rocketcrew.pocat.domain.order.dto.response;

import com.rocketcrew.pocat.domain.order.entity.Order;

import java.time.LocalDateTime;

public record OrderResponse(
        Long orderId,
        String orderUid,
        Long auctionId,
        String cardName,
        String cardGrade,
        String cardImageUrl,
        Long finalPrice,
        String orderStatus,
        String deliveryStatus,
        LocalDateTime createdAt
) {
    public static OrderResponse of(
            Order order,
            String cardName,
            String cardGrade,
            String cardImageUrl
    ) {
        return new OrderResponse(
                order.getId(),
                order.getOrderUid(),
                order.getAuctionId(),
                cardName,
                cardGrade,
                cardImageUrl,
                order.getFinalPrice(),
                order.getStatus().name(),
                order.getDeliveryStatus().name(),
                order.getCreatedAt()
        );
    }
}
