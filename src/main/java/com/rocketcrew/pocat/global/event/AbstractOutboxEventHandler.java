package com.rocketcrew.pocat.global.event;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

public abstract class AbstractOutboxEventHandler<E extends BaseEvent> {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(E event) {
        publish(event);
    }

    protected abstract void publish(E event);
}
