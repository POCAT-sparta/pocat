package com.rocketcrew.pocat.domain.payment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.PaymentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.OffsetDateTime;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortOneClient {

    private final RestClient portOneRestClient;

    public PortOnePaymentResponse getPayment(String paymentUid) {
        try {
            PortOneRawResponse raw = portOneRestClient.get()
                    .uri("/payments/{paymentUid}", paymentUid)
                    .retrieve()
                    .body(PortOneRawResponse.class);
            return toResponse(raw);
        } catch (RestClientException e) {
            log.error("PortOne 결제 조회 실패 - paymentUid: {}", paymentUid, e);
            throw new PaymentException(ErrorCode.PORTONE_NOT_INTEGRATED, e);
        }
    }

    public PortOnePaymentResponse attemptBillingKeyPayment(
            String paymentUid,
            String billingKey,
            Long amount
    ) {
        Map<String, Object> body = Map.of(
                "billingKey", billingKey,
                "orderName", "POCAT 경매 낙찰",
                "amount", Map.of("total", amount),
                "currency", "KRW"
        );

        try {
            PortOneRawResponse raw = portOneRestClient.post()
                    .uri("/payments/{paymentUid}/billing-key", paymentUid)
                    .body(body)
                    .retrieve()
                    .body(PortOneRawResponse.class);
            return toResponse(raw);
        } catch (RestClientException e) {
            log.error("PortOne 빌링키 결제 실패 - paymentUid: {}", paymentUid, e);
            throw new PaymentException(ErrorCode.PORTONE_NOT_INTEGRATED, e);
        }
    }

    private PortOnePaymentResponse toResponse(PortOneRawResponse raw) {
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
                raw.paidAt() != null ? OffsetDateTime.parse(raw.paidAt()).toLocalDateTime() : null
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PortOneRawResponse(
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
    }
}
