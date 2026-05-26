package com.rocketcrew.pocat.domain.auction.event;

import com.rocketcrew.pocat.global.event.BaseEvent;
import lombok.Getter;

@Getter
public class AuctionActivatedEvent extends BaseEvent {

    private final Long auctionId;
    private final Long sellerId;

    public AuctionActivatedEvent(Long auctionId, Long sellerId) {
        super("auction.activated");
        this.auctionId = auctionId;
        this.sellerId = sellerId;
    }
}
