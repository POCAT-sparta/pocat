package com.rocketcrew.pocat.domain.order.enums;

public enum OrderStatus {
    PAYMENT_PENDING,
    DIRECT_PAYMENT_FAILED, // 직접결제 실패
    AUTO_PAYMENT_FAILED, // 자동결제 실패
    CANCELLED,
    PAYMENT_COMPLETED,
    SHIPPING,
    SHIPPING_COMPLETED,
    ORDER_COMPLETED,
    REFUNDED
}
