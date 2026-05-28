package com.rocketcrew.pocat.domain.payment.dto.request;

/**
 * PortOne Webhook 수신 DTO.
 * Bean Validation 어노테이션을 의도적으로 제거함.
 * 컨트롤러에서 byte[]로 수신 후 ObjectMapper로 직접 역직렬화하므로
 * @Valid / @NotBlank 등의 Jakarta Validation이 실행되지 않는다.
 * 실제 검증은 PaymentCommandService.handleWebhook()의 수동 null 체크로 수행한다.
 */
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

    public record WebhookAmount(
            Long total
    ) {}
}
