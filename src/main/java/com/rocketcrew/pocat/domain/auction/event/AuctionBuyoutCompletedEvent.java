package com.rocketcrew.pocat.domain.auction.event;

import com.rocketcrew.pocat.global.event.BaseEvent;
import lombok.Getter;

@Getter
public class AuctionBuyoutCompletedEvent extends BaseEvent {

    private final Long auctionId;
    private final Long orderId;
    private final String orderUid;
    private final Long buyerId;
    private final Long sellerId;
    private final Long cardId;
    private final Long finalPrice;
    private final Long previousHighestBidderId;

    public AuctionBuyoutCompletedEvent(Long auctionId, Long orderId, String orderUid,
                                       Long buyerId, Long sellerId, Long cardId,
                                       Long finalPrice, Long previousHighestBidderId) {
        super(AuctionEventType.BUYOUT_COMPLETED);
        this.auctionId = auctionId;
        this.orderId = orderId;
        this.orderUid = orderUid;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.cardId = cardId;
        this.finalPrice = finalPrice;
        this.previousHighestBidderId = previousHighestBidderId;
    }
}
