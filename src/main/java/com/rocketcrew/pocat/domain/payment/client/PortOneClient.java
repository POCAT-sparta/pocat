package com.rocketcrew.pocat.domain.payment.client;

import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.PaymentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
            return PortOneRawResponse.toResponse(raw);
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
            return PortOneRawResponse.toResponse(raw);
        } catch (RestClientException e) {
            log.error("PortOne 빌링키 결제 실패 - paymentUid: {}", paymentUid, e);
            throw new PaymentException(ErrorCode.BILLING_PAYMENT_FAILED,e);
        }
    }
}
