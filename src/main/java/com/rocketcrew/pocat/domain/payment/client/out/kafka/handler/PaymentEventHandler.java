package com.rocketcrew.pocat.domain.payment.client.out.kafka.handler;

import com.rocketcrew.pocat.domain.payment.client.out.kafka.event.PaymentBaseEvent;
import com.rocketcrew.pocat.domain.payment.client.out.kafka.producer.PaymentEventProducer;
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
public class PaymentEventHandler {

    private final PaymentEventProducer paymentEventProducer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(PaymentBaseEvent event) {
        paymentEventProducer.publish(event);
    }
}
