package com.rocketcrew.pocat.domain.order.kafka.consumer.paymentEvent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.order.service.OrderCommandService;
import com.rocketcrew.pocat.domain.order.snapshot.service.OrderSnapshotCommandService;
import com.rocketcrew.pocat.domain.payment.event.PaymentEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaymentEventConsumer {

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

    // 결제 완료 → 주문 상태 변경 → 스냅샷 생성
    private void handlePaymentCompleted(PaymentEvent event) {
        orderCommandService.completePayment(event.getOrderUid());
        orderSnapshotCommandService.createSnapshot(event.getOrderUid());
    }

    // 자동결제 실패 → 주문 상태 변경 + 1시간 직접결제 데드라인 설정
    private void handlePaymentAutoFailed(PaymentEvent event) {
        orderCommandService.schedulePaymentDeadline(event.getOrderUid());
    }

    // 직접결제 실패 → 주문 상태 변경 (재시도 가능, 1시간 TTL 만료 시 ExpiryEventListener가 승격 처리)
    private void handlePaymentDirectFailed(PaymentEvent event) {
        orderCommandService.failDirectPayment(event.getOrderUid());
    }
}
