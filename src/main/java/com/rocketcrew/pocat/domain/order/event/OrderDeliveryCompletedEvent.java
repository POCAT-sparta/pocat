package com.rocketcrew.pocat.domain.order.event;

import com.rocketcrew.pocat.global.event.BaseEvent;
import lombok.Getter;

@Getter
public class OrderDeliveryCompletedEvent extends BaseEvent {

    private final String orderUid;
    private final Long buyerId;
    private final Long sellerId;

    public OrderDeliveryCompletedEvent(String orderUid, Long buyerId,
                                       Long sellerId) {
        super("order.delivery.completed");
        this.orderUid = orderUid;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
    }

}
