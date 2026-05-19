package com.rocketcrew.pocat.domain.payment.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record WebhookRequest(
        @NotBlank String type,
        @NotBlank String timestamp,
        @NotNull @Valid WebhookData data
) {
    public record WebhookData(
            @NotBlank String paymentId,     // 우리 paymentUid
            String transactionId,
            String storeId,
            @NotNull @Valid WebhookAmount amount,
            @NotBlank String status         // PAID / FAILED / CANCELLED
    ) {}

    public record WebhookAmount(
            @NotNull @Positive Long total
    ) {}
}
