package com.rocketcrew.pocat.domain.auction.kafka;

import com.rocketcrew.pocat.domain.auction.event.AuctionInspectionPassedEvent;
import com.rocketcrew.pocat.global.event.AbstractOutboxEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuctionInspectionPassedEventHandler extends AbstractOutboxEventHandler<AuctionInspectionPassedEvent> {

    private final AuctionEventProducer auctionEventProducer;

    @Override
    protected void publish(AuctionInspectionPassedEvent event) {
        auctionEventProducer.sendInspectionPassed(event);
    }
}
