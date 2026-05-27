package com.rocketcrew.pocat.domain.auction.event;

import com.rocketcrew.pocat.global.event.BaseEvent;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class AuctionInspectionPassedEvent extends BaseEvent {

    private final Long auctionId;
    private final Long sellerId;
    private final String auctionTitle;
    private final LocalDateTime approvedAt;

    public AuctionInspectionPassedEvent(Long auctionId, Long sellerId,
                                        String auctionTitle, LocalDateTime approvedAt) {
        super("auction.inspection.passed");
        this.auctionId = auctionId;
        this.sellerId = sellerId;
        this.auctionTitle = auctionTitle;
        this.approvedAt = approvedAt;
    }
}
