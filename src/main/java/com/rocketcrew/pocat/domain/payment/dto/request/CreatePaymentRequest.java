package com.rocketcrew.pocat.domain.payment.dto.request;

import jakarta.validation.constraints.NotNull;

public record CreatePaymentRequest(
        @NotNull(message = "주문 ID는 필수입니다.")
        Long orderId
) {}
