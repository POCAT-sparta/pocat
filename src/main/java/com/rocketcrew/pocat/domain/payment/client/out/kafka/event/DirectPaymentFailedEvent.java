package com.rocketcrew.pocat.domain.payment.client.out.kafka.event;

import lombok.Getter;

@Getter
public class DirectPaymentFailedEvent extends PaymentBaseEvent {

    private final Long buyerId;
    private final Long sellerId;

    public DirectPaymentFailedEvent(String orderUid, Long buyerId, Long sellerId) {
        super("payment.direct.failed", orderUid);
        this.buyerId = buyerId;
        this.sellerId = sellerId;
    }
}
