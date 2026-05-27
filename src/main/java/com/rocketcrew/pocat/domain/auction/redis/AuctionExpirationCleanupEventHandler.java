package com.rocketcrew.pocat.domain.auction.redis;

import com.rocketcrew.pocat.domain.auction.event.AuctionEndedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AuctionExpirationCleanupEventHandler {

    private final AuctionExpirationRedisService auctionExpirationRedisService;

    // 경매 종료 트랜잭션이 커밋된 뒤 shadow key를 삭제해 종료 처리 완료 상태로 정리한다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deleteShadowKey(AuctionEndedEvent event) {
        auctionExpirationRedisService.deleteShadowKey(event.getAuctionId());
    }
}
