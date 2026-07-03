package com.rocketcrew.pocat.domain.bid.service;

import com.rocketcrew.pocat.domain.bid.event.BidOutbidEvent;
import com.rocketcrew.pocat.domain.bid.producer.BidEventProducer;
import com.rocketcrew.pocat.global.event.AbstractOutboxEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BidOutbidEventHandler extends AbstractOutboxEventHandler<BidOutbidEvent> {

    private final BidEventProducer bidEventProducer;

    @Override
    protected void publish(BidOutbidEvent event) {
        bidEventProducer.sendBidOutbid(event);
    }
}
