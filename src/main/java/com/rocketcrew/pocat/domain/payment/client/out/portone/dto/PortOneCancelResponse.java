package com.rocketcrew.pocat.domain.payment.client.out.portone.dto;

import com.rocketcrew.pocat.domain.payment.client.out.portone.PortOneCancelStatus;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PortOneCancelResponse(
        PortOneCancelStatus status,
        String pgId,
        String pgCancellationId,
        Long totalAmount,
        Long taxFreeAmount,
        String reason,
        LocalDateTime cancelledAt,
        LocalDateTime requestedAt
) {


}
