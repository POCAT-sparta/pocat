package com.rocketcrew.pocat.global.scheduler;

import com.rocketcrew.pocat.domain.community.freepost.service.FreePostRankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FreePostRankingScheduler {

    private final FreePostRankingService rankingService;

    @Scheduled(fixedDelay = 60_000)
    public void refresh() {
        rankingService.refreshRanking();
    }
}
