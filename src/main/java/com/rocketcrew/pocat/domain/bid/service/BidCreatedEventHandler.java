package com.rocketcrew.pocat.domain.bid.service;

import com.rocketcrew.pocat.domain.bid.event.BidCreatedEvent;
import com.rocketcrew.pocat.domain.bid.producer.BidEventProducer;
import com.rocketcrew.pocat.global.event.AbstractOutboxEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BidCreatedEventHandler extends AbstractOutboxEventHandler<BidCreatedEvent> {

    private final BidEventProducer bidEventProducer;

    @Override
    protected void publish(BidCreatedEvent event) {
        bidEventProducer.sendBidCreated(event);
    }
}
