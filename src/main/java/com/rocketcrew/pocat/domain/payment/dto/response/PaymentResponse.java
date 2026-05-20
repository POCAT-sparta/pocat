package com.rocketcrew.pocat.domain.payment.dto.response;

import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import com.rocketcrew.pocat.domain.payment.entity.PaymentType;

import java.time.LocalDateTime;

public record PaymentResponse(
        String paymentUid,
        Long orderId,
        Long amount,
        PaymentType paymentType,
        String paymentMethod,   // 결제 확정 전까지 null
        PaymentStatus status,
        LocalDateTime paidAt,
        LocalDateTime createdAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getPaymentUid(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getPaymentType(),
                payment.getPaymentMethod(),
                payment.getStatus(),
                payment.getPaidAt(),
                payment.getCreatedAt()
        );
    }
}
