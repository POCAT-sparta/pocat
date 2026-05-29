package com.rocketcrew.pocat.domain.payment.event;

import lombok.Getter;

@Getter
public class AutoPaymentFailedEvent extends PaymentBaseEvent {

    private final Long buyerId;
    private final Long sellerId;

    public AutoPaymentFailedEvent(String orderUid,Long buyerId, Long sellerId) {
        super("payment.auto.failed", orderUid);
        this.buyerId = buyerId;
        this.sellerId = sellerId;
    }
}
