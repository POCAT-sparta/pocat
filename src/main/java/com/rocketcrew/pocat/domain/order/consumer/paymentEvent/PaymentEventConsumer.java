package com.rocketcrew.pocat.domain.order.consumer.paymentEvent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.notification.enums.NotificationType;
import com.rocketcrew.pocat.domain.notification.service.NotificationCommandService;
import com.rocketcrew.pocat.domain.order.service.OrderCommandService;
import com.rocketcrew.pocat.domain.order.snapshot.service.OrderSnapshotCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final NotificationCommandService notificationCommandService;
    private final OrderCommandService orderCommandService;
    private final OrderSnapshotCommandService orderSnapshotCommandService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "payment",
            groupId = "order-payment-group",
            containerFactory = "paymentKafkaListenerContainerFactory")
    public void consume(String message, Acknowledgment acknowledgment) {
        try {
            PaymentEvent event = objectMapper.readValue(message, PaymentEvent.class);
            switch (event.getEventType()) {
                case "payment.completed"     -> handlePaymentCompleted(event);
                case "payment.auto.failed"   -> handlePaymentAutoFailed(event);
                case "payment.direct.failed" -> handlePaymentDirectFailed(event);
                default -> log.debug("처리 대상 아닌 payment 이벤트: {}", event.getEventType());
            }
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("payment 이벤트 처리 실패: {}", message, e);
            throw new RuntimeException(e);
        }
    }

    // 결제 완료 → 주문 상태 변경 → 스냅샷 생성 → 구매자/판매자 알림
    private void handlePaymentCompleted(PaymentEvent event) {
        orderCommandService.completePayment(event.getOrderUid());
        orderSnapshotCommandService.createSnapshot(event.getOrderUid());

        try {
            notificationCommandService.send(
                    event.getBuyerId(),
                    NotificationType.PAYMENT_COMPLETED,
                    "결제가 완료되었습니다.",
                    Map.of("orderUid", event.getOrderUid(), "finalPrice", event.getFinalPrice())
            );
        } catch (Exception e) {
            log.error("결제완료 구매자 알림 실패: orderUid={}", event.getOrderUid(), e);
        }

        try {
            notificationCommandService.send(
                    event.getSellerId(),
                    NotificationType.PAYMENT_COMPLETED,
                    "구매자의 결제가 완료되었습니다.",
                    Map.of("orderUid", event.getOrderUid(), "finalPrice", event.getFinalPrice())
            );
        } catch (Exception e) {
            log.error("결제완료 판매자 알림 실패: orderUid={}", event.getOrderUid(), e);
        }
    }

    // 자동결제 실패 → 주문 상태 변경 + 1시간 직접결제 데드라인 설정 → 구매자 알림
    private void handlePaymentAutoFailed(PaymentEvent event) {
        orderCommandService.schedulePaymentDeadline(event.getOrderUid());

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

    // 직접결제 실패 → 주문 상태 변경 → 구매자 알림 → 판매자 알림 TODO:  다음 순위 있으면 바로 주문 생성 후 자동결제? 없으면 판매자 알림 후 재등록??
    private void handlePaymentDirectFailed(PaymentEvent event) {
        orderCommandService.failDirectPayment(event.getOrderUid());

        try {
            notificationCommandService.send(
                    event.getBuyerId(),
                    NotificationType.DIRECT_PAYMENT_FAILED,
                    "결제에 실패했습니다.",
                    Map.of("orderUid", event.getOrderUid())
            );
        } catch (Exception e) {
            log.error("직접결제 실패 구매자 알림 실패: orderUid={}", event.getOrderUid(), e);
        }

        try {
            notificationCommandService.send(
                    event.getSellerId(),
                    NotificationType.DIRECT_PAYMENT_FAILED,
                    "구매자의 결제가 최종 실패하였습니다.",
                    Map.of("orderUid", event.getOrderUid())
            );
        } catch (Exception e) {
            log.error("직접결제 실패 판매자 알림 실패: orderUid={}", event.getOrderUid(), e);
        }
    }
}
