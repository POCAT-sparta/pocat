package com.rocketcrew.pocat.domain.bid.service;

import com.rocketcrew.pocat.domain.bid.event.BidCreatedEvent;
import com.rocketcrew.pocat.domain.bid.event.BidOutbidEvent;
import com.rocketcrew.pocat.domain.bid.producer.BidEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class BidEventHandler {

    private final BidEventProducer bidEventProducer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(BidCreatedEvent event) {
        bidEventProducer.sendBidCreated(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(BidOutbidEvent event) {
        bidEventProducer.sendBidOutbid(event);
    }
}
