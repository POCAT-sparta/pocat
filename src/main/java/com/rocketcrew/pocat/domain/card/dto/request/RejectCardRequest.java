package com.rocketcrew.pocat.domain.card.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RejectCardRequest(
        @NotBlank(message = "거절 사유는 필수입니다.")
        String rejectReason
) {
}
