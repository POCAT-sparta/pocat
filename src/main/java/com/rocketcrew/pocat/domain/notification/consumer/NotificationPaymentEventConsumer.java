package com.rocketcrew.pocat.domain.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.notification.enums.NotificationType;
import com.rocketcrew.pocat.domain.notification.service.NotificationCommandService;
import com.rocketcrew.pocat.domain.payment.event.PaymentEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPaymentEventConsumer {

    private final NotificationCommandService notificationCommandService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "payment",
            groupId = "notification-payment-group",
            containerFactory = "paymentKafkaListenerContainerFactory")
    public void consume(String message, Acknowledgment acknowledgment) {
        try {
            PaymentNotificationEventPayload event = objectMapper.readValue(message, PaymentNotificationEventPayload.class);
            switch (event.getEventType()) {
                case PaymentEventType.COMPLETED     -> handlePaymentCompleted(event);
                case PaymentEventType.AUTO_FAILED   -> handlePaymentAutoFailed(event);
                case PaymentEventType.DIRECT_FAILED -> handlePaymentDirectFailed(event);
                default -> log.debug("처리 대상 아닌 payment 이벤트: {}", event.getEventType());
            }
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("payment 이벤트 처리 실패: {}", message, e);
            throw new RuntimeException(e);
        }
    }

    // 결제 완료 → 구매자/판매자 알림
    private void handlePaymentCompleted(PaymentNotificationEventPayload event) {
        Map<String, Object> completedPayload = new HashMap<>();
        completedPayload.put("orderUid", event.getOrderUid());
        if (event.getFinalPrice() != null) {
            completedPayload.put("finalPrice", event.getFinalPrice());
        }

        try {
            notificationCommandService.send(
                    event.getBuyerId(),
                    NotificationType.PAYMENT_COMPLETED,
                    "결제가 완료되었습니다.",
                    completedPayload
            );
        } catch (Exception e) {
            log.error("결제완료 구매자 알림 실패: orderUid={}", event.getOrderUid(), e);
        }

        try {
            notificationCommandService.send(
                    event.getSellerId(),
                    NotificationType.PAYMENT_COMPLETED,
                    "구매자의 결제가 완료되었습니다.",
                    completedPayload
            );
        } catch (Exception e) {
            log.error("결제완료 판매자 알림 실패: orderUid={}", event.getOrderUid(), e);
        }
    }

    // 자동결제 실패 → 구매자 알림
    private void handlePaymentAutoFailed(PaymentNotificationEventPayload event) {
        try {
            notificationCommandService.send(
                    event.getBuyerId(),
                    NotificationType.AUTO_PAYMENT_FAILED,
                    "자동결제에 실패했습니다. 직접 결제를 진행해 주세요.",
                    Map.of("orderUid", event.getOrderUid())
            );
        } catch (Exception e) {
            log.error("자동결제 실패 알림 실패: orderUid={}", event.getOrderUid(), e);
        }
    }

    // 직접결제 실패 → 구매자 알림
    private void handlePaymentDirectFailed(PaymentNotificationEventPayload event) {
        try {
            notificationCommandService.send(
                    event.getBuyerId(),
                    NotificationType.DIRECT_PAYMENT_FAILED,
                    "결제에 실패했습니다. 1시간 내에 다시 시도해 주세요.",
                    Map.of("orderUid", event.getOrderUid())
            );
        } catch (Exception e) {
            log.error("직접결제 실패 구매자 알림 실패: orderUid={}", event.getOrderUid(), e);
        }
    }
}
