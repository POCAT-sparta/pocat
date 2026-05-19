package com.rocketcrew.pocat.domain.refund.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RejectRefundRequest(
        @NotBlank(message = "거절 사유는 필수입니다.")
        String rejectReason
) {}
