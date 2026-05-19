package com.rocketcrew.pocat.domain.payment.dto.request;

public record WebhookRequest(
        String type,
        String timestamp,
        WebhookData data
) {
    public record WebhookData(
            String paymentId,       // 우리 paymentUid
            String transactionId,
            String storeId,
            WebhookAmount amount,
            String status           // PAID / FAILED / CANCELLED
    ) {}

    public record WebhookAmount(Long total) {}
}
