package com.rocketcrew.pocat.domain.payment.dto.response;

import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import com.rocketcrew.pocat.domain.payment.entity.PaymentType;

import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long orderId,
        String paymentUid,
        Long amount,
        PaymentType paymentType,
        String paymentMethod,
        PaymentStatus status,
        LocalDateTime paidAt,
        LocalDateTime createdAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getPaymentUid(),
                payment.getAmount(),
                payment.getPaymentType(),
                payment.getPaymentMethod(),
                payment.getStatus(),
                payment.getPaidAt(),
                payment.getCreatedAt()
        );
    }
}
