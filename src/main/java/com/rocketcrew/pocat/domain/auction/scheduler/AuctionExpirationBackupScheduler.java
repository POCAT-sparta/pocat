package com.rocketcrew.pocat.domain.auction.scheduler;

import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.auction.service.AuctionLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionExpirationBackupScheduler {

    private final AuctionRepository auctionRepository;
    private final AuctionLifecycleService auctionLifecycleService;

    // 매일 19:05~19:30에 Redis 만료 이벤트로 처리되지 않은 만료 경매를 DB 기준으로 보정 종료한다.
    @Scheduled(cron = "0 5-30 19 * * *", zone = "Asia/Seoul")
    public void closeExpiredAuctions() {
        List<Auction> expiredAuctions = auctionRepository
                .findAllByStatusAndEndedAtLessThanEqualOrderByEndedAtAsc(AuctionStatus.ACTIVE, LocalDateTime.now());
        for (Auction auction : expiredAuctions) {
            try {
                auctionLifecycleService.closeExpiredAuction(auction.getId());
            } catch (Exception e) {
                log.warn("경매 종료 백업 스케줄 처리 실패: auctionId={}", auction.getId(), e);
            }
        }
    }
}
