package com.rocketcrew.pocat.domain.order.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.notification.enums.NotificationType;
import com.rocketcrew.pocat.domain.notification.service.NotificationCommandService;
import com.rocketcrew.pocat.domain.payment.event.PaymentBillingRequestedEvent;
import com.rocketcrew.pocat.domain.payment.producer.PaymentEventProducer;
import com.rocketcrew.pocat.domain.settlement.service.SettlementCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final NotificationCommandService notificationCommandService;
    private final PaymentEventProducer paymentEventProducer;
    private final SettlementCommandService settlementCommandService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "order",
            groupId = "order-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(String message) {
        try {
            OrderEvent event = objectMapper.readValue(message, OrderEvent.class);
            switch (event.getEventType()) {
                case "order.created"            -> handleOrderCreated(event);
                case "order.cancelled"          -> handleOrderCancelled(event);
                case "order.delivery.started"   -> handleDeliveryStarted(event);
                case "order.delivery.completed" -> handleDeliveryCompleted(event);
                default -> log.warn("알 수 없는 order 이벤트: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("주문 이벤트 처리 실패: {}", message, e);
            throw new RuntimeException(e);
        }
    }

    // 주문 생성 → 낙찰 알림 + 자동결제 요청
    private void handleOrderCreated(OrderEvent event) {
        try {
            notificationCommandService.send(
                    event.getBuyerId(),
                    NotificationType.AUCTION_WON,
                    "낙찰을 축하합니다! 자동결제가 진행됩니다.",
                    Map.of("orderUid", event.getOrderUid(), "finalPrice", event.getFinalPrice())
            );
        } catch (Exception e) {
            log.error("구매자 낙찰 알림 실패: orderUid={}", event.getOrderUid(), e);
        }

        try {
            notificationCommandService.send(
                    event.getSellerId(),
                    NotificationType.AUCTION_WON,
                    "카드가 낙찰되었습니다!",
                    Map.of("orderUid", event.getOrderUid(), "finalPrice", event.getFinalPrice())
            );
        } catch (Exception e) {
            log.error("판매자 낙찰 알림 실패: orderUid={}", event.getOrderUid(), e);
        }

        // 자동결제 요청 이벤트 발행 (멱등성은 payment 도메인에서 보장)
        try {
            paymentEventProducer.sendBillingRequested(
                    new PaymentBillingRequestedEvent(
                            event.getOrderUid(),
                            event.getBuyerId(),
                            event.getFinalPrice()
                    )
            );
        } catch (Exception e) {
            log.error("자동결제 요청 실패: orderUid={}", event.getOrderUid(), e);
        }
    }

    // 주문 취소 → 구매자/판매자 알림
    private void handleOrderCancelled(OrderEvent event) {
        try {
            notificationCommandService.send(
                    event.getBuyerId(),
                    NotificationType.ORDER_CANCELLED,
                    "주문이 취소되었습니다.",
                    Map.of("orderUid", event.getOrderUid())
            );
        } catch (Exception e) {
            log.error("구매자 주문취소 알림 실패: orderUid={}", event.getOrderUid(), e);
        }

        try {
            notificationCommandService.send(
                    event.getSellerId(),
                    NotificationType.ORDER_CANCELLED,
                    "주문이 취소되었습니다.",
                    Map.of("orderUid", event.getOrderUid())
            );
        } catch (Exception e) {
            log.error("판매자 주문취소 알림 실패: orderUid={}", event.getOrderUid(), e);
        }
    }

    // 배송 시작 → 구매자 알림
    private void handleDeliveryStarted(OrderEvent event) {
        try {
            notificationCommandService.send(
                    event.getBuyerId(),
                    NotificationType.SHIPPING,
                    "배송이 시작되었습니다.",
                    Map.of("orderUid", event.getOrderUid())
            );
        } catch (Exception e) {
            log.error("배송 시작 알림 실패: orderUid={}", event.getOrderUid(), e);
        }
    }

    // 배송 완료 → 구매자 알림 + 정산 생성
    private void handleDeliveryCompleted(OrderEvent event) {
        try {
            notificationCommandService.send(
                    event.getBuyerId(),
                    NotificationType.SHIPPING_COMPLETED,
                    "배송이 완료되었습니다.",
                    Map.of("orderUid", event.getOrderUid())
            );
        } catch (Exception e) {
            log.error("배송 완료 알림 실패: orderUid={}", event.getOrderUid(), e);
        }

        // 정산 생성 (createSettlement 내부에서 중복 생성 방지)
        try {
            settlementCommandService.createSettlement(event.getOrderUid());
        } catch (Exception e) {
            log.error("정산 생성 실패: orderUid={}", event.getOrderUid(), e);
        }
    }
}
