package com.rocketcrew.pocat.domain.payment.client;

import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.PaymentException;
import org.springframework.stereotype.Component;

@Component
public class PortOneClient {

    public PortOnePaymentResponse getPayment(String paymentUid) {
        // TODO: WebClient로 GET https://api.portone.io/v2/payments/{paymentUid} 호출
        // Authorization: PortOne API 시크릿 키
        throw new PaymentException(ErrorCode.PORTONE_NOT_INTEGRATED);
    }

    public PortOnePaymentResponse attemptBillingKeyPayment(
            String paymentUid,
            String billingKey,
            Long amount
    ) {
        // TODO: POST https://api.portone.io/v2/payments/{paymentUid}/billing-key
        throw new PaymentException(ErrorCode.PORTONE_NOT_INTEGRATED);
    }
}
