package com.rocketcrew.pocat.domain.payment.client.out.portone;

import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.PaymentException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum PortOneStatus {
    PAID("PAID", "결제 완료"),
    CANCELLED("CANCELLED", "결제 취소건"),
    FAILED("FAILED", "결제 실패"),
    PARTIAL_CANCELLED("PARTIAL_CANCELLED", "부분 취소"),
    PAY_PENDING("PAY_PENDING", "완료 대기"),
    READY("READY", "결제 준비"),
    NETWORK_ERROR("NETWORK_ERROR", "포트원 통신 에러");

    public static PortOneStatus from(String code) {
        return Arrays.stream(values())
                .filter(s -> s.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new PaymentException(ErrorCode.PORTONE_NOT_INTEGRATED));
    }

    private final String code;
    private final String message;
}
