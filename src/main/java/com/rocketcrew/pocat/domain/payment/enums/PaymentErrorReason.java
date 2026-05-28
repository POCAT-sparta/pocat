package com.rocketcrew.pocat.domain.payment.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorReason {
    AMOUNT_NULL("결제 금액 정보가 누락되었습니다."),
    AMOUNT_MISMATCH("결제 요청 금액과 실결제 금액이 일치하지 않습니다."),
    USER_CANCELLED("사용자가 결제를 취소하였습니다."),
    WEBHOOK_FAILED("웹훅 수신 상태가 실패(FAILED)입니다."),
    PAYMENT_EXPIRED("결제 시간이 초과되었습니다.");

    private final String description;
}
