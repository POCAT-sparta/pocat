package com.rocketcrew.pocat.domain.refund.dto.response;

import com.rocketcrew.pocat.domain.refund.entity.Refund;
import com.rocketcrew.pocat.domain.refund.entity.RefundStatus;

import java.time.LocalDateTime;

public record RefundResponse(
        Long id,
        Long orderId,
        Long paymentId,
        Long amount,
        String reason,
        String rejectReason,
        RefundStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static RefundResponse from(Refund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getOrderId(),
                refund.getPaymentId(),
                refund.getAmount(),
                refund.getReason(),
                refund.getRejectReason(),
                refund.getStatus(),
                refund.getCreatedAt(),
                refund.getUpdatedAt()
        );
    }
}
