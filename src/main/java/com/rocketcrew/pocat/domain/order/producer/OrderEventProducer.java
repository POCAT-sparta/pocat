package com.rocketcrew.pocat.domain.order.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.order.event.OrderCancelledEvent;
import com.rocketcrew.pocat.domain.order.event.OrderCreatedEvent;
import com.rocketcrew.pocat.domain.order.event.OrderDeliveryCompletedEvent;
import com.rocketcrew.pocat.domain.order.event.OrderDeliveryStartedEvent;
import com.rocketcrew.pocat.global.event.BaseEventProducer;
import com.rocketcrew.pocat.global.outbox.repository.OutboxRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventProducer extends BaseEventProducer {

    private static final String TOPIC = "order";

    public OrderEventProducer(KafkaTemplate<String, String> kafkaTemplate,
                              ObjectMapper objectMapper,
                              OutboxRepository outboxRepository) {
        super(kafkaTemplate, objectMapper, outboxRepository);
    }

    // 주문 생성
    public void sendOrderCreated(OrderCreatedEvent event) {
        send(TOPIC, event.getOrderUid(), event.getOutboxId(), event);
    }

    // 주문 취소
    public void sendOrderCancelled(OrderCancelledEvent event) {
        send(TOPIC, event.getOrderUid(), event.getOutboxId(), event);
    }

    // 배송 시작
    public void sendDeliveryStarted(OrderDeliveryStartedEvent event) {
        send(TOPIC, event.getOrderUid(), event.getOutboxId(), event);
    }

    // 배송 완료
    public void sendDeliveryCompleted(OrderDeliveryCompletedEvent event) {
        send(TOPIC, event.getOrderUid(), event.getOutboxId(), event);
    }
}
