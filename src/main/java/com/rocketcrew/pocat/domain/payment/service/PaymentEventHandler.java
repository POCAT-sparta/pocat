package com.rocketcrew.pocat.domain.payment.service;

import com.rocketcrew.pocat.domain.payment.event.PaymentBillingRequestedEvent;
import com.rocketcrew.pocat.domain.payment.event.PaymentCompletedEvent;
import com.rocketcrew.pocat.domain.payment.event.PaymentFailedEvent;
import com.rocketcrew.pocat.domain.payment.event.PaymentWindowExpiredEvent;
import com.rocketcrew.pocat.domain.payment.producer.PaymentEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventHandler {

    private final PaymentEventProducer paymentEventProducer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(PaymentBillingRequestedEvent event) {
        paymentEventProducer.sendBillingRequested(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(PaymentCompletedEvent event) {
        paymentEventProducer.sendPaymentCompleted(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(PaymentFailedEvent event) {
        paymentEventProducer.sendPaymentFailed(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(PaymentWindowExpiredEvent event) {
        paymentEventProducer.sendWindowExpired(event);
    }
}
