package com.rocketcrew.pocat.domain.bid.event;

import com.rocketcrew.pocat.global.event.BaseEvent;
import lombok.Getter;

@Getter
public class BidOutbidEvent extends BaseEvent {

    private final Long auctionId;
    private final Long previousBidderId;
    private final Long currentHighestPrice;

    public BidOutbidEvent(Long auctionId, Long previousBidderId,
                          Long currentHighestPrice) {
        super("bid.outbid");
        this.auctionId = auctionId;
        this.previousBidderId = previousBidderId;
        this.currentHighestPrice = currentHighestPrice;
    }
}
