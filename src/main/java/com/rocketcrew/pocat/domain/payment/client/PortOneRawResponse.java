package com.rocketcrew.pocat.domain.payment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.PaymentException;
import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOneRawResponse(
        String status,
        AmountDetail amount,
        MethodDetail method,
        FailureDetail failure,
        String paidAt
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record AmountDetail(Long total) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MethodDetail(String type) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FailureDetail(String code, String message) {}

    private static java.time.LocalDateTime parsePaidAt(String paidAt) {
        if (paidAt == null) return null;
        try {
            return OffsetDateTime.parse(paidAt).toLocalDateTime();
        } catch (DateTimeParseException e) {
            log.error("PortOne paidAt 파싱 실패 - value: {}", paidAt, e);
            throw new PaymentException(ErrorCode.PORTONE_INVALID_PAID_AT, e);
        }
    }

    public static PortOnePaymentResponse toResponse(PortOneRawResponse raw) {
        if (raw == null) {
            throw new PaymentException(ErrorCode.PORTONE_NOT_INTEGRATED);
        }

        if (raw.failure() != null) {
            log.warn("PortOne 결제 실패 - code: {}, message: {}", raw.failure().code(), raw.failure().message());
        }

        return new PortOnePaymentResponse(
                raw.status(),
                raw.amount() != null ? raw.amount().total() : null,
                raw.method() != null ? raw.method().type() : null,
                parsePaidAt(raw.paidAt())
        );
    }
}
