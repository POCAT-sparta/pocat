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
public record PortOnePaymentRawResponse(
        String status,
        AmountDetail amount,
        MethodDetail method,
        FailureDetail failure,
        String pgTxId,
        String paidAt
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record AmountDetail(Long total) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MethodDetail(String type) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FailureDetail(String reason, String pgCode, String pgMessage) {}

    private static LocalDateTime parsePaidAt(String paidAt) {
        if (paidAt == null) return null;
        try {
            return OffsetDateTime.parse(paidAt).toLocalDateTime();
        } catch (DateTimeParseException e) {
            log.error("PortOne paidAt 파싱 실패 - value: {}", paidAt, e);
            throw new PaymentException(ErrorCode.PORTONE_INVALID_PAID_AT, e);
        }
    }

    public static PortOnePaymentResponse toResponse(PortOnePaymentRawResponse raw) {
        if (raw == null) {
            throw new PaymentException(ErrorCode.PORTONE_NOT_INTEGRATED);
        }

        PortOnePaymentResponse.PortOnePaymentResponseBuilder builder = PortOnePaymentResponse.builder()
                .status(PortOneStatus.from(raw.status()))
                .amount(raw.amount() != null ? raw.amount().total() : null)
                .paymentMethod(raw.method() != null ? raw.method().type() : null)
                .paidAt(parsePaidAt(raw.paidAt()))
                .portOneTxId(raw.pgTxId());

        if (raw.failure() != null) {
            builder.failReason(raw.failure().reason())
                    .pgCode(raw.failure().pgCode())
                    .pgMessage(raw.failure().pgMessage());
        }

        return builder.build();
    }

    public static PortOnePaymentResponse toNetworkError() {
        return PortOnePaymentResponse.builder()
                .status(PortOneStatus.NETWORK_ERROR)
                .failReason(PortOneStatus.NETWORK_ERROR.getMessage())
                .build();
    }
}
