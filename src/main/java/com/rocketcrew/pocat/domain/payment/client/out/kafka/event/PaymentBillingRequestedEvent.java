package com.rocketcrew.pocat.domain.payment.client.out.kafka.event;

import lombok.Getter;

@Getter
public class PaymentBillingRequestedEvent extends PaymentBaseEvent {

    private final Long buyerId;
    private final Long finalPrice;

    public PaymentBillingRequestedEvent(String orderUid, Long buyerId, Long finalPrice) {
        super("payment.billing.requested", orderUid);
        this.buyerId = buyerId;
        this.finalPrice = finalPrice;
    }
}
