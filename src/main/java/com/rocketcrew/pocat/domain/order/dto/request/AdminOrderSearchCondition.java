package com.rocketcrew.pocat.domain.order.dto.request;

import com.rocketcrew.pocat.domain.card.entity.CardGrade;
import com.rocketcrew.pocat.domain.order.enums.DeliveryStatus;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;

import java.time.LocalDateTime;

public record AdminOrderSearchCondition(
        OrderStatus orderStatus,
        DeliveryStatus deliveryStatus,
        CardGrade cardGrade,
        LocalDateTime startDate,
        LocalDateTime endDate,
        String buyerNickname,
        String sellerNickname
) {
}
