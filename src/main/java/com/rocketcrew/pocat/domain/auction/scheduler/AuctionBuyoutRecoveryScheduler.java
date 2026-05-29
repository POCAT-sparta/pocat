package com.rocketcrew.pocat.domain.auction.scheduler;

import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.auction.service.AuctionBuyoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionBuyoutRecoveryScheduler {

    private static final ZoneId AUCTION_ZONE = ZoneId.of("Asia/Seoul");
    private static final long PAYMENT_PENDING_RECOVERY_MINUTES = 2L;

    private final AuctionRepository auctionRepository;
    private final AuctionBuyoutService auctionBuyoutService;

    // PAYMNET_PENDING 상태가 오래 유지된 경매를 주기적으로 찾아 복구하는 메서드
    @Scheduled(fixedDelay = 60_000)
    public void recoverStalePaymentPendingAuctions() {
        // 복구 대상 기준 시각 계산. 2분으로 설정
        LocalDateTime cutoff = LocalDateTime.now(AUCTION_ZONE).minusMinutes(PAYMENT_PENDING_RECOVERY_MINUTES);
        // PAYMENT_PENDING 상태이고 updatedAt이 기준 시각보다 오래된 경매 목록을 조회합니다.
        List<Auction> staleAuctions = auctionRepository
                .findAllByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
                        AuctionStatus.PAYMENT_PENDING,
                        cutoff
                );

        for (Auction auction : staleAuctions) {
            try {
                // 조회된 경매에 실제 복구 판단 위임
                boolean recovered = auctionBuyoutService.recoverStalePaymentPendingAuction(auction.getId());
                if (recovered) {
                    log.info("Recovered stale buyout auction. auctionId={}", auction.getId());
                }
            } catch (Exception e) {
                log.warn("Failed to recover stale buyout auction. auctionId={}", auction.getId(), e);
            }
        }
    }
}
