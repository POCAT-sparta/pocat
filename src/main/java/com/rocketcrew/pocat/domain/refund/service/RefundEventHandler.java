package com.rocketcrew.pocat.domain.refund.service;

import com.rocketcrew.pocat.domain.refund.event.RefundApprovedEvent;
import com.rocketcrew.pocat.domain.refund.event.RefundRejectedEvent;
import com.rocketcrew.pocat.domain.refund.event.RefundRequestedEvent;
import com.rocketcrew.pocat.domain.refund.producer.RefundEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class RefundEventHandler {

    private final RefundEventProducer refundEventProducer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(RefundRequestedEvent event) {
        refundEventProducer.sendRefundRequested(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(RefundApprovedEvent event) {
        refundEventProducer.sendRefundApproved(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(RefundRejectedEvent event) {
        refundEventProducer.sendRefundRejected(event);
    }
}
