package com.rocketcrew.pocat.domain.auction.event;

import com.rocketcrew.pocat.global.event.BaseEvent;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.AuctionException;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class AuctionActivatedEvent extends BaseEvent {

    private final Long auctionId;
    private final Long sellerId;
    private final LocalDateTime endedAt;

    // 경매 활성화 후 Kafka 발행과 Redis TTL 등록에 필요한 정보를 담는다.
    public AuctionActivatedEvent(Long auctionId, Long sellerId, LocalDateTime endedAt) {
        super(AuctionEventType.ACTIVATED);
        if (auctionId == null || endedAt == null) {
            throw new AuctionException(ErrorCode.AUCTION_EVENT_INVALID_PAYLOAD);
        }
        this.auctionId = auctionId;
        this.sellerId = sellerId;
        this.endedAt = endedAt;
    }
}
