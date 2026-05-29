package com.rocketcrew.pocat.domain.payment.client.out.portone.dto;

import com.rocketcrew.pocat.domain.payment.client.out.portone.PortOneStatus;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PortOneCancelResponse(
        PortOneStatus status,
        String pgId,
        String pgCancellationId,
        Long totalAmount,
        Long taxFreeAmount,
        String reason,
        LocalDateTime cancelledAt,
        LocalDateTime requestedAt
) {


}
