package com.rocketcrew.pocat.domain.payment.client.out.kafka.event;

import com.rocketcrew.pocat.global.event.BaseEvent;
import lombok.Getter;

@Getter
public abstract class PaymentBaseEvent extends BaseEvent {

    private final String orderUid;

    protected PaymentBaseEvent(String eventType, String orderUid) {
        super(eventType);
        this.orderUid = orderUid;
    }
}
