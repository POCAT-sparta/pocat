package com.rocketcrew.pocat.domain.auction.scheduler;

import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.auction.service.AuctionLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionActivationScheduler {

    private final AuctionRepository auctionRepository;
    private final AuctionLifecycleService auctionLifecycleService;

    // 매일 19시에 승인 경매를 ACTIVE로 전환하고, 실제 처리 시각을 시작 시각으로 확정한다.
    @Scheduled(cron = "0 0 19 * * *", zone = "Asia/Seoul")
    public void activateApprovedAuctions() {
        List<Auction> approvedAuctions = auctionRepository.findAllByStatus(AuctionStatus.APPROVED);
        for (Auction auction : approvedAuctions) {
            try {
                auctionLifecycleService.activateApprovedAuction(auction.getId());
            } catch (Exception e) {
                log.warn("경매 활성화 스케줄 처리 실패: auctionId={}", auction.getId(), e);
            }
        }
    }
}
