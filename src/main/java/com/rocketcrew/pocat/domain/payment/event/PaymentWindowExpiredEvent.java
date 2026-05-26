package com.rocketcrew.pocat.domain.payment.event;

import com.rocketcrew.pocat.global.event.BaseEvent;
import lombok.Getter;

@Getter
public class PaymentWindowExpiredEvent extends BaseEvent {

    private final String orderUid;
    private final Long buyerId;
    private final Long sellerId;
    private final Integer bidderRank; // 1순위 or 2순위

    public PaymentWindowExpiredEvent(String orderUid, Long buyerId,
                                     Long sellerId, Integer bidderRank) {
        super("payment.window.expired");
        this.orderUid = orderUid;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.bidderRank = bidderRank;
    }
}
