package com.rocketcrew.pocat.internal.testscenario.order.dto;

import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.service.EscalationResult;

public record OrderPaymentDeadlineInjectionResponse(
        String orderUid,
        Long orderId,
        OrderStatus beforeOrderStatus,
        EscalationResult.Status escalationStatus,
        Long nextBidderId,
        String nextOrderUid,
        boolean expiryKeyCancelled,
        String verificationHint
) {
}
