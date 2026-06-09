package com.rocketcrew.pocat.domain.order.service;

import com.rocketcrew.pocat.domain.order.event.OrderCancelledEvent;
import com.rocketcrew.pocat.domain.order.event.OrderCreatedEvent;
import com.rocketcrew.pocat.domain.order.producer.OrderEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventHandler {

    private final OrderEventProducer orderEventProducer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(OrderCreatedEvent event) {
        orderEventProducer.sendOrderCreated(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(OrderCancelledEvent event) {
        orderEventProducer.sendOrderCancelled(event);
    }

}
