package com.rocketcrew.pocat.domain.payment.client.out.portone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.rocketcrew.pocat.domain.payment.client.out.portone.PortOneStatus;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.PaymentException;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOneCancelRawResponse(
        CancellationDetail cancellation  // 래퍼 필드
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record CancellationDetail(
            String status,
            String id,
            String pgCancellationId,
            Long totalAmount,
            Long taxFreeAmount,
            String reason,
            String cancelledAt,
            String requestedAt
    ) {}

    private static LocalDateTime parsePaidAt(String paidAt) {
        if (paidAt == null) return null;
        try {
            return OffsetDateTime.parse(paidAt).toLocalDateTime();
        } catch (DateTimeParseException e) {
            log.error("PortOne paidAt 파싱 실패 - value: {}", paidAt, e);
            throw new PaymentException(ErrorCode.PORTONE_INVALID_PAID_AT, e);
        }
    }

    public static PortOneCancelResponse toResponse(PortOneCancelRawResponse raw) {
        if (raw == null || raw.cancellation() == null) {
            throw new PaymentException(ErrorCode.PORTONE_NOT_INTEGRATED);
        }
        CancellationDetail c = raw.cancellation();

        return PortOneCancelResponse.builder()
                .status(PortOneStatus.from(c.status()))
                .pgId(c.id())
                .pgCancellationId(c.pgCancellationId())
                .totalAmount(c.totalAmount())
                .taxFreeAmount(c.taxFreeAmount())
                .reason(c.reason())
                .cancelledAt(parsePaidAt(c.cancelledAt()))
                .requestedAt(parsePaidAt(c.requestedAt()))
                .build();
    }

    public static PortOneCancelResponse toNetworkError() {
        return PortOneCancelResponse.builder()
                .status(PortOneStatus.NETWORK_ERROR)
                .reason(PortOneStatus.NETWORK_ERROR.getMessage())
                .build();
    }
}