package com.rocketcrew.pocat.domain.auction.kafka;

import com.rocketcrew.pocat.domain.auction.event.AuctionInspectionFailedEvent;
import com.rocketcrew.pocat.global.event.AbstractOutboxEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuctionInspectionFailedEventHandler extends AbstractOutboxEventHandler<AuctionInspectionFailedEvent> {

    private final AuctionEventProducer auctionEventProducer;

    @Override
    protected void publish(AuctionInspectionFailedEvent event) {
        auctionEventProducer.sendInspectionFailed(event);
    }
}
