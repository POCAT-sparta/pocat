package com.rocketcrew.pocat.domain.refund.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateRefundRequest(
        @NotNull(message = "주문 ID는 필수입니다.")
        Long orderId,

        @NotBlank(message = "환불 사유는 필수입니다.")
        String reason
) {}
