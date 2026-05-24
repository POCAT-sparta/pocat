package com.rocketcrew.pocat.domain.auction.service;

import com.rocketcrew.pocat.domain.auction.event.*;
import com.rocketcrew.pocat.domain.auction.producer.AuctionEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuctionEventHandler {

    private final AuctionEventProducer auctionEventProducer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AuctionInspectionPassedEvent event) {
        auctionEventProducer.sendInspectionPassed(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AuctionInspectionFailedEvent event) {
        auctionEventProducer.sendInspectionFailed(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AuctionActivatedEvent event) {
        auctionEventProducer.sendActivated(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AuctionCancelledEvent event) {
        auctionEventProducer.sendCancelled(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AuctionEndedEvent event) {
        auctionEventProducer.sendEnded(event);
    }
}

