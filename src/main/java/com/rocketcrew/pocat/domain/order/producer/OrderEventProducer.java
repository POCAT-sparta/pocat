package com.rocketcrew.pocat.domain.order.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.order.kafka.event.OrderCancelledEvent;
import com.rocketcrew.pocat.domain.order.kafka.event.OrderCreatedEvent;
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
    // 트랜잭션 경계(REQUIRES_NEW)는 AbstractOutboxEventHandler 에 있다. 여기선 발행만 위임한다.
    public void sendOrderCreated(OrderCreatedEvent event) {
        send(TOPIC, event.getOrderUid(), event.getOutboxId(), event);
    }

    // 주문 취소
    public void sendOrderCancelled(OrderCancelledEvent event) {
        send(TOPIC, event.getOrderUid(), event.getOutboxId(), event);
    }

}
