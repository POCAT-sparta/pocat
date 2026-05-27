package com.rocketcrew.pocat.domain.auction.event;

import com.rocketcrew.pocat.global.event.BaseEvent;
import lombok.Getter;

import java.util.List;

@Getter
public class AuctionCancelledEvent extends BaseEvent {

    private final Long auctionId;
    private final Long sellerId;
    private final Long cancelledBy;
    private final String reason;
    private final List<Long> bidderIds;

    public AuctionCancelledEvent(Long auctionId, Long sellerId,
                                 Long cancelledBy, String reason,
                                 List<Long> bidderIds) {
        super("auction.cancelled");
        this.auctionId = auctionId;
        this.sellerId = sellerId;
        this.cancelledBy = cancelledBy;
        this.reason = reason;
        this.bidderIds = bidderIds != null ? List.copyOf(bidderIds) : List.of();
    }
}
