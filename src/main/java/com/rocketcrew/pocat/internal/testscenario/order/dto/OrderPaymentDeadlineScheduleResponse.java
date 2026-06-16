package com.rocketcrew.pocat.internal.testscenario.order.dto;

import com.rocketcrew.pocat.domain.order.enums.OrderStatus;

import java.time.LocalDateTime;

public record OrderPaymentDeadlineScheduleResponse(
        String orderUid,
        Long orderId,
        OrderStatus status,
        LocalDateTime beforePaymentDeadline,
        LocalDateTime afterPaymentDeadline,
        long ttlSeconds,
        boolean redisExpiryKeyScheduled,
        String expectedFlow
) {
}
