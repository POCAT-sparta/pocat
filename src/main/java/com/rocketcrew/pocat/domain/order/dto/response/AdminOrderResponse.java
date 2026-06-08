package com.rocketcrew.pocat.domain.order.dto.response;

import com.rocketcrew.pocat.domain.order.enums.OrderStatus;

import java.time.LocalDateTime;

public record AdminOrderResponse(
        Long orderId,
        String orderUid,
        String buyerNickname,
        String sellerNickname,
        String cardName,
        String cardGrade,
        Long finalPrice,
        OrderStatus status,
        LocalDateTime createdAt
) {
}
