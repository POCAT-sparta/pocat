package com.rocketcrew.pocat.domain.payment.client.out.portone;

import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.PaymentException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum PortOneCancelStatus {
    FAILED("FAILED", "취소 실패"),
    REQUESTED("REQUESTED", "취소 요청됨"),
    SUCCEEDED("SUCCEEDED", "취소 성공"),
    HTTP_ERROR("HTTP_ERROR", "HTTP 오류"),
    NETWORK_ERROR("NETWORK_ERROR", "포트원 통신 에러");

    public static PortOneCancelStatus from(String code) {
        return Arrays.stream(values())
                .filter(s -> s.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new PaymentException(ErrorCode.PORTONE_CANCEL_STATUS_UNKNOWN));
    }

    private final String code;
    private final String message;
}
