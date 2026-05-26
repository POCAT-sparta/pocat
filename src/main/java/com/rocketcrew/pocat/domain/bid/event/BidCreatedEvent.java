package com.rocketcrew.pocat.domain.bid.event;

import com.rocketcrew.pocat.global.event.BaseEvent;
import lombok.Getter;

@Getter
public class BidCreatedEvent extends BaseEvent {

    private final Long auctionId;
    private final Long sellerId;
    private final Long bidderId;
    private final Long bidPrice;

    public BidCreatedEvent(Long auctionId, Long sellerId,
                           Long bidderId, Long bidPrice) {
        super("bid.created");
        this.auctionId = auctionId;
        this.sellerId = sellerId;
        this.bidderId = bidderId;
        this.bidPrice = bidPrice;
    }
}
