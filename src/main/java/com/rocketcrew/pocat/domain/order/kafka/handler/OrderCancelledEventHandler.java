package com.rocketcrew.pocat.domain.order.kafka.handler;

import com.rocketcrew.pocat.domain.order.kafka.event.OrderCancelledEvent;
import com.rocketcrew.pocat.domain.order.producer.OrderEventProducer;
import com.rocketcrew.pocat.global.event.AbstractOutboxEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderCancelledEventHandler extends AbstractOutboxEventHandler<OrderCancelledEvent> {

    private final OrderEventProducer orderEventProducer;

    @Override
    protected void publish(OrderCancelledEvent event) {
        orderEventProducer.sendOrderCancelled(event);
    }
}
