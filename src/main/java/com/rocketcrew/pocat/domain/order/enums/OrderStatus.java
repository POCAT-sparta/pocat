package com.rocketcrew.pocat.domain.order.enums;

public enum OrderStatus {
    PAYMENT_PENDING,
    PAYMENT_FAILED,
    CANCELLED,
    PAYMENT_COMPLETED,
    SHIPPING,
    SHIPPING_COMPLETED,
    ORDER_COMPLETED,
    REFUNDED
}
