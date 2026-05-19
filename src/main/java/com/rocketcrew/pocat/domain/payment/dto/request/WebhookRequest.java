package com.rocketcrew.pocat.domain.payment.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record WebhookRequest(
        @NotBlank(message = "type은 필수입니다.")
        String type,
        String timestamp,
        @NotNull(message = "data는 필수입니다.")
        @Valid
        WebhookData data
) {
    public record WebhookData(
            @NotBlank(message = "paymentId는 필수입니다.")
            String paymentId,       // 우리 paymentUid
            String transactionId,
            String storeId,
            @NotNull(message = "amount는 필수입니다.")
            @Valid
            WebhookAmount amount,
            @NotBlank(message = "status는 필수입니다.")
            String status           // PAID / FAILED / CANCELLED
    ) {}

    public record WebhookAmount(
            @NotNull(message = "total은 필수입니다.")
            @PositiveOrZero(message = "total은 0 이상이어야 합니다.")
            Long total
    ) {}
}
