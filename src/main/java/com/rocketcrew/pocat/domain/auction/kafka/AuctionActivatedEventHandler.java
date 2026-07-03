package com.rocketcrew.pocat.domain.auction.kafka;

import com.rocketcrew.pocat.domain.auction.event.AuctionActivatedEvent;
import com.rocketcrew.pocat.global.event.AbstractOutboxEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuctionActivatedEventHandler extends AbstractOutboxEventHandler<AuctionActivatedEvent> {

    private final AuctionEventProducer auctionEventProducer;

    @Override
    protected void publish(AuctionActivatedEvent event) {
        auctionEventProducer.sendActivated(event);
    }
}
