package com.rocketcrew.pocat.domain.auction.event;

import com.rocketcrew.pocat.global.event.BaseEvent;
import lombok.Getter;

import java.util.List;

@Getter
public class AuctionCancelledEvent extends BaseEvent {

    private final Long auctionId;
    private final Long sellerId;
    private final List<Long> bidderIds; // 입찰자들 알림용

    public AuctionCancelledEvent(Long auctionId, Long sellerId,
                                 List<Long> bidderIds) {
        super("auction.cancelled");
        this.auctionId = auctionId;
        this.sellerId = sellerId;
        this.bidderIds = bidderIds != null ? List.copyOf(bidderIds) : List.of();
    }
}
