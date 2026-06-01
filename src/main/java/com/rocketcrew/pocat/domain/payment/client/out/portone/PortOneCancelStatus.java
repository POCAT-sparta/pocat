package com.rocketcrew.pocat.domain.payment.client.out.portone;

import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.PaymentException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum PortOneCancelStatus {
    FAILED("FAILED", "결제 완료"),
    REQUESTED("REQUESTED", "결제 완료"),
    SUCCEEDED("SUCCEEDED", "결제 완료"),
    HTTP_ERROR("HTTP_ERROR", "결제 완료"),
    NETWORK_ERROR("NETWORK_ERROR", "포트원 통신 에러");

    public static PortOneCancelStatus from(String code) {
        return Arrays.stream(values())
                .filter(s -> s.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new PaymentException(ErrorCode.PORTONE_NOT_INTEGRATED));
    }

    private final String code;
    private final String message;
}
