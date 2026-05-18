package com.rocketcrew.pocat.domain.refund.dto.request;

public record CreateRefundRequest(
        Long orderId,
        Long paymentId,
        Long amount,
        String reason
) {
}
