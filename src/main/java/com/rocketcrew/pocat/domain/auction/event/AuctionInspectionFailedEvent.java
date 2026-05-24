package com.rocketcrew.pocat.domain.auction.event;

import com.rocketcrew.pocat.global.event.BaseEvent;
import lombok.Getter;

@Getter
public class AuctionInspectionFailedEvent extends BaseEvent {

    private final Long auctionId;
    private final Long sellerId;
    private final String cardName;
    private final String failedReason;

    public AuctionInspectionFailedEvent(Long auctionId, Long sellerId,
                                        String cardName, String failedReason) {
        super("auction.inspection.failed");
        this.auctionId = auctionId;
        this.sellerId = sellerId;
        this.cardName = cardName;
        this.failedReason = failedReason;
    }
}
