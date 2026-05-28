package com.rocketcrew.pocat.domain.payment.event;

import com.rocketcrew.pocat.global.event.BaseEvent;
import lombok.Getter;

@Getter
public class PaymentFailedEvent extends BaseEvent {

    private final String orderUid;
    private final Long buyerId;
    private final String reason;
    private final String failureType; // "AUTO" | "DIRECT"

    public PaymentFailedEvent(String orderUid, Long buyerId, String reason, String failureType) {
        super("payment.failed");
        this.orderUid = orderUid;
        this.buyerId = buyerId;
        this.reason = reason;
        this.failureType = failureType;
    }
}
