package com.rocketcrew.pocat.domain.bid.event;

import com.rocketcrew.pocat.global.event.BaseEvent;
import lombok.Getter;

@Getter
public class BidOutbidEvent extends BaseEvent {

    private final Long auctionId;
    private final Long previousBidderId; // 밀려난 입찰자
    private final Long bidPrice;         // 새 입찰가

    public BidOutbidEvent(Long auctionId, Long previousBidderId,
                          Long bidPrice) {
        super("bid.outbid");
        this.auctionId = auctionId;
        this.previousBidderId = previousBidderId;
        this.bidPrice = bidPrice;
    }
}
