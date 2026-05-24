package com.rocketcrew.pocat.domain.settlement.service;

import com.rocketcrew.pocat.domain.settlement.event.SettlementCompletedEvent;
import com.rocketcrew.pocat.domain.settlement.event.SettlementCreatedEvent;
import com.rocketcrew.pocat.domain.settlement.producer.SettlementEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class SettlementEventHandler {

    private final SettlementEventProducer settlementEventProducer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(SettlementCreatedEvent event) {
        settlementEventProducer.sendSettlementCreated(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(SettlementCompletedEvent event) {
        settlementEventProducer.sendSettlementCompleted(event);
    }
}
