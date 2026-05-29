package com.rocketcrew.pocat.domain.auction.redis;

import com.rocketcrew.pocat.domain.auction.event.AuctionActivatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AuctionExpirationRegistrationEventHandler {

    private final AuctionExpirationRedisService auctionExpirationRedisService;

    // 경매 활성화 트랜잭션이 커밋된 뒤 Redis 종료 TTL 키와 shadow key를 생성한다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AuctionActivatedEvent event) {
        auctionExpirationRedisService.setExpirationKeys(event.getAuctionId(), event.getEndedAt());
    }
}
