package com.rocketcrew.pocat.domain.auction.kafka;

import com.rocketcrew.pocat.domain.auction.event.AuctionEndedEvent;
import com.rocketcrew.pocat.global.event.AbstractOutboxEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuctionEndedEventHandler extends AbstractOutboxEventHandler<AuctionEndedEvent> {

    private final AuctionEventProducer auctionEventProducer;

    @Override
    protected void publish(AuctionEndedEvent event) {
        auctionEventProducer.sendEnded(event);
    }
}
