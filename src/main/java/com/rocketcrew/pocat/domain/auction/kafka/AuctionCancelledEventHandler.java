package com.rocketcrew.pocat.domain.auction.kafka;

import com.rocketcrew.pocat.domain.auction.event.AuctionCancelledEvent;
import com.rocketcrew.pocat.global.event.AbstractOutboxEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuctionCancelledEventHandler extends AbstractOutboxEventHandler<AuctionCancelledEvent> {

    private final AuctionEventProducer auctionEventProducer;

    @Override
    protected void publish(AuctionCancelledEvent event) {
        auctionEventProducer.sendCancelled(event);
    }
}
