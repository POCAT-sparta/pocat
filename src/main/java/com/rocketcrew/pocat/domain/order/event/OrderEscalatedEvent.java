package com.rocketcrew.pocat.domain.order.event;

import com.rocketcrew.pocat.domain.order.service.EscalationResult;
import com.rocketcrew.pocat.global.event.BaseEvent;
import lombok.Getter;

@Getter
public class OrderEscalatedEvent extends BaseEvent {

    private final EscalationResult.Status status;
    private final Long nextBidderId;
    private final String nextOrderUid;
    private final Long sellerId;
    private final String orderUid;

    public OrderEscalatedEvent(EscalationResult.Status status, Long nextBidderId, String nextOrderUid,
                                Long sellerId, String orderUid) {
        super("order.escalated");
        this.status = status;
        this.nextBidderId = nextBidderId;
        this.nextOrderUid = nextOrderUid;
        this.sellerId = sellerId;
        this.orderUid = orderUid;
    }
}
