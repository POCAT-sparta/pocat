package com.rocketcrew.pocat.domain.order.kafka.event;

import com.rocketcrew.pocat.global.event.BaseEvent;
import lombok.Getter;

@Getter
public class OrderCancelledEvent extends BaseEvent {

    private final String orderUid;
    private final Long buyerId;
    private final Long sellerId;
    private final String reason;

    public OrderCancelledEvent(String orderUid, Long buyerId,
                               Long sellerId, String reason) {
        super("order.cancelled");
        this.orderUid = orderUid;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.reason = reason;
    }
}
