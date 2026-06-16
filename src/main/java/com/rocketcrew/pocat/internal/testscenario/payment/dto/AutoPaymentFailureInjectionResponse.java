package com.rocketcrew.pocat.internal.testscenario.payment.dto;

import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.enums.OrderType;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import com.rocketcrew.pocat.domain.payment.entity.PaymentType;

public record AutoPaymentFailureInjectionResponse(
        String orderUid,
        Long orderId,
        OrderType orderType,
        OrderStatus beforeOrderStatus,
        OrderStatus afterOrderStatus,
        String paymentUid,
        PaymentType paymentType,
        PaymentStatus beforePaymentStatus,
        PaymentStatus afterPaymentStatus,
        boolean paymentCreated,
        boolean autoFailureEventPublished,
        boolean directPaymentAvailable
) {
}
