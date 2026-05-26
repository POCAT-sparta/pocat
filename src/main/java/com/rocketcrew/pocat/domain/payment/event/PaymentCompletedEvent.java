package com.rocketcrew.pocat.domain.payment.event;

import com.rocketcrew.pocat.global.event.BaseEvent;
import lombok.Getter;

@Getter
public class PaymentCompletedEvent extends BaseEvent {

    private final String orderUid;
    private final Long buyerId;
    private final Long sellerId;
    private final Long finalPrice;

    public PaymentCompletedEvent(String orderUid, Long buyerId,
                                 Long sellerId, Long finalPrice) {
        super("payment.completed");
        this.orderUid = orderUid;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.finalPrice = finalPrice;
    }
}
