package com.rocketcrew.pocat.domain.payment.event;

import lombok.Getter;

@Getter
public class PaymentCompletedEvent extends PaymentBaseEvent {

    private final Long buyerId;
    private final Long sellerId;
    private final Long finalPrice;

    public PaymentCompletedEvent(String orderUid, Long buyerId,
                                 Long sellerId, Long finalPrice) {
        super("payment.completed", orderUid);
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.finalPrice = finalPrice;
    }
}
