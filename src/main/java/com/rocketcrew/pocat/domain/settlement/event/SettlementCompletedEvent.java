package com.rocketcrew.pocat.domain.settlement.event;

import com.rocketcrew.pocat.global.event.BaseEvent;
import lombok.Getter;

@Getter
public class SettlementCompletedEvent extends BaseEvent {

    private final String settlementUid;
    private final Long sellerId;
    private final Long sellerAmount;

    public SettlementCompletedEvent(String settlementUid, Long sellerId,
                                    Long sellerAmount) {
        super("settlement.completed");
        this.settlementUid = settlementUid;
        this.sellerId = sellerId;
        this.sellerAmount = sellerAmount;
    }
}
