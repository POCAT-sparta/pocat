package com.rocketcrew.pocat.domain.order.dto.request;

import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;

import java.time.LocalDateTime;

public record AdminOrderSearchCondition(
        OrderStatus orderStatus,
        CardGrade cardGrade,
        LocalDateTime startDate,
        LocalDateTime endDate,
        String buyerNickname,
        String sellerNickname
) {
}
