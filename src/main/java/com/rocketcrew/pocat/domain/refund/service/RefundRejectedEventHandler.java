package com.rocketcrew.pocat.domain.refund.service;

import com.rocketcrew.pocat.domain.refund.event.RefundRejectedEvent;
import com.rocketcrew.pocat.domain.refund.producer.RefundEventProducer;
import com.rocketcrew.pocat.global.event.AbstractOutboxEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefundRejectedEventHandler extends AbstractOutboxEventHandler<RefundRejectedEvent> {

    private final RefundEventProducer refundEventProducer;

    @Override
    protected void publish(RefundRejectedEvent event) {
        refundEventProducer.sendRefundRejected(event);
    }
}
