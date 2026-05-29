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
    private static final long PAYMENT_PENDING_RECOVERY_MINUTES = 10L;

    private final AuctionRepository auctionRepository;
    private final AuctionBuyoutService auctionBuyoutService;

    @Scheduled(fixedDelay = 60_000)
    public void recoverStalePaymentPendingAuctions() {
        LocalDateTime cutoff = LocalDateTime.now(AUCTION_ZONE).minusMinutes(PAYMENT_PENDING_RECOVERY_MINUTES);
        List<Auction> staleAuctions = auctionRepository
                .findAllByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
                        AuctionStatus.PAYMENT_PENDING,
                        cutoff
                );

        for (Auction auction : staleAuctions) {
            try {
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
