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

    private static final int MAX_RETRY = 3;

    private final RestClient portOneRestClient;

    /**
     * 결제 조회. 네트워크 오류 시 최대 3회 재시도(1s, 2s 간격).
     * PENDING·READY 상태는 최종 상태가 아닐 수 있으나 재조회는 호출부에서 판단한다.
     */
    public PortOnePaymentResponse getPayment(String paymentUid) {
        RestClientException lastEx = null;
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                PortOneRawResponse raw = portOneRestClient.get()
                        .uri("/payments/{paymentUid}", paymentUid)
                        .retrieve()
                        .body(PortOneRawResponse.class);
                return toResponse(raw);
            } catch (RestClientException e) {
                lastEx = e;
                log.warn("PortOne 결제 조회 실패 paymentUid={} attempt={}/{}", paymentUid, attempt, MAX_RETRY, e);
                if (attempt < MAX_RETRY) sleepSeconds(attempt);
            }
        }
        log.error("PortOne 결제 조회 최대 재시도 초과 paymentUid={}", paymentUid, lastEx);
        throw new PaymentException(ErrorCode.PORTONE_NOT_INTEGRATED, lastEx);
    }

    /**
     * 빌링키 자동결제. 네트워크 오류 시 최대 3회 재시도.
     * PortOne이 이미 청구를 시작했을 수 있으므로 재시도 전 상태 조회는 호출부에서 처리한다.
     */
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
        RestClientException lastEx = null;
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                PortOneRawResponse raw = portOneRestClient.post()
                        .uri("/payments/{paymentUid}/billing-key", paymentUid)
                        .body(body)
                        .retrieve()
                        .body(PortOneRawResponse.class);
                return toResponse(raw);
            } catch (RestClientException e) {
                lastEx = e;
                log.warn("PortOne 빌링키 결제 실패 paymentUid={} attempt={}/{}", paymentUid, attempt, MAX_RETRY, e);
                if (attempt < MAX_RETRY) {
                    // 중복 청구 방지: 재시도 전 이미 결제가 완료됐는지 확인
                    // (1회 청구 성공 후 응답 수신 전 타임아웃 → 재시도 시 AlreadyPaidError 방지)
                    try {
                        PortOneRawResponse check = portOneRestClient.get()
                                .uri("/payments/{paymentUid}", paymentUid)
                                .retrieve()
                                .body(PortOneRawResponse.class);
                        if (check != null && "PAID".equals(check.status())) {
                            log.info("빌링키 결제 이미 완료 확인, 재시도 생략 paymentUid={}", paymentUid);
                            return toResponse(check);
                        }
                    } catch (Exception checkEx) {
                        log.warn("재시도 전 결제 상태 조회 실패, 재시도 진행 paymentUid={}", paymentUid, checkEx);
                    }
                    sleepSeconds(attempt);
                }
            }
        }
        log.error("PortOne 빌링키 결제 최대 재시도 초과 paymentUid={}", paymentUid, lastEx);
        throw new PaymentException(ErrorCode.PORTONE_NOT_INTEGRATED, lastEx);
    }

    /**
     * 결제 취소(환불). 네트워크 오류 시 최대 3회 재시도.
     * PortOne 취소 API는 멱등성을 보장하므로 재시도가 안전하다.
     */
    public void cancelPayment(String paymentUid, Long amount, String reason) {
        Map<String, Object> body = Map.of(
                "reason", reason,
                "amount", Map.of("total", amount)
        );
        RestClientException lastEx = null;
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                portOneRestClient.post()
                        .uri("/payments/{paymentUid}/cancel", paymentUid)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();
                return;
            } catch (RestClientException e) {
                lastEx = e;
                log.warn("PortOne 결제 취소 실패 paymentUid={} attempt={}/{}", paymentUid, attempt, MAX_RETRY, e);
                if (attempt < MAX_RETRY) sleepSeconds(attempt);
            }
        }
        log.error("PortOne 결제 취소 최대 재시도 초과 paymentUid={}", paymentUid, lastEx);
        throw new PaymentException(ErrorCode.PORTONE_CANCEL_FAILED, lastEx);
    }

    private PortOnePaymentResponse toResponse(PortOneRawResponse raw) {
        if (raw == null) {
            throw new PaymentException(ErrorCode.PORTONE_NOT_INTEGRATED);
        }
        if (raw.failure() != null) {
            log.warn("PortOne 결제 실패 응답 code={} message={}", raw.failure().code(), raw.failure().message());
        }
        return new PortOnePaymentResponse(
                raw.status(),
                raw.amount() != null ? raw.amount().total() : null,
                raw.method() != null ? raw.method().type() : null,
                raw.paidAt() != null ? OffsetDateTime.parse(raw.paidAt()).toLocalDateTime() : null
        );
    }

    /**
     * 재시도 전 대기. InterruptedException 발생 시 interrupt 상태를 복원한다.
     */
    private void sleepSeconds(int seconds) {
        try {
            Thread.sleep(1000L * seconds);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new PaymentException(ErrorCode.PORTONE_NOT_INTEGRATED, ie);
        }
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
