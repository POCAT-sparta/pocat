package com.rocketcrew.pocat.domain.order.event;

import com.rocketcrew.pocat.global.event.BaseEvent;
import lombok.Getter;

@Getter
public class OrderDeliveryStartedEvent extends BaseEvent {

    private final String orderUid;
    private final Long buyerId;

    public OrderDeliveryStartedEvent(String orderUid, Long buyerId) {
        super("order.delivery.started");
        this.orderUid = orderUid;
        this.buyerId = buyerId;
    }
}
