package com.rocketcrew.pocat.domain.order.kafka.handler;

import com.rocketcrew.pocat.domain.order.kafka.event.OrderCreatedEvent;
import com.rocketcrew.pocat.domain.order.producer.OrderEventProducer;
import com.rocketcrew.pocat.global.event.AbstractOutboxEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderCreatedEventHandler extends AbstractOutboxEventHandler<OrderCreatedEvent> {

    private final OrderEventProducer orderEventProducer;

    @Override
    protected void publish(OrderCreatedEvent event) {
        orderEventProducer.sendOrderCreated(event);
    }
}
