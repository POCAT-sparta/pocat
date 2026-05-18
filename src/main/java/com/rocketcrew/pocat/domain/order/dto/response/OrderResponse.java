package com.rocketcrew.pocat.domain.order.dto.response;

import com.rocketcrew.pocat.domain.order.entity.DeliveryStatus;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.entity.OrderStatus;

import java.time.LocalDateTime;

public record OrderResponse(
        Long id,
        Long auctionId,
        Long cardId,
        Long sellerId,
        Long buyerId,
        String orderUid,
        Long finalPrice,
        OrderStatus status,
        DeliveryStatus deliveryStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getAuctionId(),
                order.getCardId(),
                order.getSellerId(),
                order.getBuyerId(),
                order.getOrderUid(),
                order.getFinalPrice(),
                order.getStatus(),
                order.getDeliveryStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
