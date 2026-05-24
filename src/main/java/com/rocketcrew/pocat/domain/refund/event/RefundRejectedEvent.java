package com.rocketcrew.pocat.domain.refund.event;

import com.rocketcrew.pocat.global.event.BaseEvent;
import lombok.Getter;

@Getter
public class RefundRejectedEvent extends BaseEvent {

    private final Long refundId;
    private final String orderUid;
    private final Long buyerId;
    private final String reason;

    public RefundRejectedEvent(Long refundId, String orderUid,
                               Long buyerId, String reason) {
        super("refund.rejected");
        this.refundId = refundId;
        this.orderUid = orderUid;
        this.buyerId = buyerId;
        this.reason = reason;
    }
}
