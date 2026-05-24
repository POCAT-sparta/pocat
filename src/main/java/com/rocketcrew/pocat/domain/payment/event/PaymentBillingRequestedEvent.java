package com.rocketcrew.pocat.domain.payment.event;

import com.rocketcrew.pocat.global.event.BaseEvent;
import lombok.Getter;

@Getter
public class PaymentBillingRequestedEvent extends BaseEvent {

    private final String orderUid;
    private final Long buyerId;
    private final Long finalPrice;
    private final String billingKey;

    public PaymentBillingRequestedEvent(String orderUid, Long buyerId,
                                        Long finalPrice, String billingKey) {
        super("payment.billing.requested");
        this.orderUid = orderUid;
        this.buyerId = buyerId;
        this.finalPrice = finalPrice;
        this.billingKey = billingKey;
    }
}
