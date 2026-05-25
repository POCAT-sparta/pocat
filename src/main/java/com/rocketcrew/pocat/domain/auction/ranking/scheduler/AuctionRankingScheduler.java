package com.rocketcrew.pocat.domain.auction.ranking.scheduler;

import com.rocketcrew.pocat.domain.auction.ranking.service.AuctionRankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionRankingScheduler {

    private final AuctionRankingService rankingService;

    @Scheduled(fixedDelay = 60_000)
    public void refresh() {
        rankingService.refreshRanking();
    }
}
