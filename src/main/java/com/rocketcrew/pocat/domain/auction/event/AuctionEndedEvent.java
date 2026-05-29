package com.rocketcrew.pocat.domain.auction.event;

import com.rocketcrew.pocat.global.event.BaseEvent;
import lombok.Getter;

import java.util.List;

@Getter
public class AuctionEndedEvent extends BaseEvent {

    private final Long auctionId;
    private final Long cardId;
    private final Long winnerId;   // 낙찰자
    private final Long sellerId;
    private final List<Long> loserIds; // 패찰자들
    private final Long finalPrice;

    public AuctionEndedEvent(Long auctionId, Long cardId, Long winnerId,
                             Long sellerId, List<Long> loserIds,
                             Long finalPrice) {
        super(AuctionEventType.ENDED);
        this.auctionId = auctionId;
        this.cardId = cardId;
        this.winnerId = winnerId;
        this.sellerId = sellerId;
        this.loserIds = loserIds != null ? List.copyOf(loserIds) : List.of();
        this.finalPrice = finalPrice;
    }
}
