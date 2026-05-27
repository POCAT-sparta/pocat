package com.rocketcrew.pocat.domain.refund.event;

import com.rocketcrew.pocat.global.event.BaseEvent;
import lombok.Getter;

@Getter
public class RefundApprovedEvent extends BaseEvent {

    private final Long refundId;
    private final String orderUid;
    private final Long buyerId;
    private final Long sellerId;

    public RefundApprovedEvent(Long refundId, String orderUid,
                               Long buyerId, Long sellerId) {
        super("refund.approved");
        this.refundId = refundId;
        this.orderUid = orderUid;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
    }
}
