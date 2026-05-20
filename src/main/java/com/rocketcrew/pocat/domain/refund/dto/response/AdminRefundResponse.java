package com.rocketcrew.pocat.domain.refund.dto.response;

import com.rocketcrew.pocat.domain.refund.entity.RefundStatus;

import java.time.LocalDateTime;

public record AdminRefundResponse(
        Long refundId,
        Long orderId,
        String buyerNickname,
        Long amount,
        String reason,
        RefundStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
