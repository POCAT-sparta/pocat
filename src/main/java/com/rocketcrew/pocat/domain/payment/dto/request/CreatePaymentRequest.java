package com.rocketcrew.pocat.domain.payment.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreatePaymentRequest(
        @NotNull(message = "주문 ID는 필수입니다.")
        @Positive(message = "주문 ID는 1 이상이어야 합니다.")
        Long orderId
) {}
