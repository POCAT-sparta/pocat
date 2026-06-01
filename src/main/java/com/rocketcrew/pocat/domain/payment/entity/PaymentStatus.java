package com.rocketcrew.pocat.domain.payment.entity;

public enum PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REFUNDED,
    CANCELLED,
    CANCEL_HTTP_ERROR
}
