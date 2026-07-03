package com.rocketcrew.pocat.domain.auction.kafka;

import com.rocketcrew.pocat.domain.auction.event.AuctionBuyoutCompletedEvent;
import com.rocketcrew.pocat.global.event.AbstractOutboxEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuctionBuyoutCompletedEventHandler extends AbstractOutboxEventHandler<AuctionBuyoutCompletedEvent> {

    private final AuctionEventProducer auctionEventProducer;

    @Override
    protected void publish(AuctionBuyoutCompletedEvent event) {
        auctionEventProducer.sendBuyoutCompleted(event);
    }
}
