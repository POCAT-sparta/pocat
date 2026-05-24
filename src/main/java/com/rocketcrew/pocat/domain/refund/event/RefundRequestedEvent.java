package com.rocketcrew.pocat.domain.refund.event;

import com.rocketcrew.pocat.global.event.BaseEvent;
import lombok.Getter;

@Getter
public class RefundRequestedEvent extends BaseEvent {

    private final Long refundId;
    private final String orderUid;
    private final Long buyerId;

    public RefundRequestedEvent(Long refundId, String orderUid, Long buyerId) {
        super("refund.requested");
        this.refundId = refundId;
        this.orderUid = orderUid;
        this.buyerId = buyerId;
    }
}
