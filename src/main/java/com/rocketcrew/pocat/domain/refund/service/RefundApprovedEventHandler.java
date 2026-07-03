package com.rocketcrew.pocat.domain.refund.service;

import com.rocketcrew.pocat.domain.refund.event.RefundApprovedEvent;
import com.rocketcrew.pocat.domain.refund.producer.RefundEventProducer;
import com.rocketcrew.pocat.global.event.AbstractOutboxEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefundApprovedEventHandler extends AbstractOutboxEventHandler<RefundApprovedEvent> {

    private final RefundEventProducer refundEventProducer;

    @Override
    protected void publish(RefundApprovedEvent event) {
        refundEventProducer.sendRefundApproved(event);
    }
}
