package com.rocketcrew.pocat.domain.payment.client.out.portone;

import com.rocketcrew.pocat.domain.payment.client.out.portone.dto.*;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.PaymentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortOneClientService {

    private final RestClient portOneRestClient;

    public PortOnePaymentResponse getPayment(String paymentUid) {
        try {
            PortOnePaymentRawResponse raw = portOneRestClient.get()
                    .uri("/payments/{paymentUid}", paymentUid)
                    .retrieve()
                    .body(PortOnePaymentRawResponse.class);

            return PortOnePaymentRawResponse.toResponse(raw);
        } catch (RestClientException e) {
            log.warn("PortOne 결제 조회 실패 paymentUid={}", paymentUid, e);
            return PortOnePaymentRawResponse.toNetworkError();
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
                "currency", "KRW");
        try {
            BillingPayRawResponse rawResponse = portOneRestClient.post()
                    .uri("/payments/{paymentUid}/billing-key", paymentUid)
                    .body(body)
                    .retrieve()
                    .body(BillingPayRawResponse.class);

            if (rawResponse == null) {
                // 결제 자체 X 연동 문제
                throw new PaymentException(ErrorCode.PORTONE_NOT_INTEGRATED);
            }

            return getPayment(paymentUid);
        }catch (RestClientException e) {
            return PortOnePaymentRawResponse.toNetworkError();
        }
    }

    public PortOneCancelResponse cancelPayment(String paymentUid, Long amount, String reason) {
        Map<String, Object> body = Map.of(
                "reason", reason,
                "amount", Map.of("total", amount == null ? 0 : amount)
        );
        try {
            PortOneCancelRawResponse raw = portOneRestClient.post()
                    .uri("/payments/{paymentUid}/cancel", paymentUid)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, ((request, response) -> {
                        throw new PaymentException(ErrorCode.PORTONE_HTTP_CODE_ERROR);
                    }))
                    .body(PortOneCancelRawResponse.class);

            return PortOneCancelRawResponse.toResponse(raw);
        } catch (RestClientException e) {
            return PortOneCancelRawResponse.toNetworkError();
        } catch (PaymentException e) {
            return PortOneCancelRawResponse.toStatusError();
        }
    }
}
