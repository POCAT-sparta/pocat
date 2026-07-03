package com.rocketcrew.pocat.domain.order.kafka.event;

import com.rocketcrew.pocat.global.event.BaseEvent;
import lombok.Getter;

@Getter
public class OrderCreatedEvent extends BaseEvent {

    private final String orderUid;
    private final Long buyerId;
    private final Long sellerId;
    private final Long finalPrice;

    public OrderCreatedEvent(String orderUid, Long buyerId,
                             Long sellerId, Long finalPrice) {
        super("order.created");
        this.orderUid = orderUid;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.finalPrice = finalPrice;
    }
}
