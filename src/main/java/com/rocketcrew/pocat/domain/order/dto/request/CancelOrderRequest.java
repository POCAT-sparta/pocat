package com.rocketcrew.pocat.domain.order.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CancelOrderRequest(
        @NotBlank(message = "취소 사유를 입력해주세요.") String reason
) {
}
