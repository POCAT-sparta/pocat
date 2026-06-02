package com.rocketcrew.pocat.global.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class AuctionMetrics {

    private final Counter registered;
    private final Counter endedSuccess;
    private final Counter endedNoBidder;

    public AuctionMetrics(MeterRegistry registry) {
        registered    = Counter.builder("auction.registered.total")
                .description("총 경매 등록 수")
                .register(registry);

        endedSuccess  = Counter.builder("auction.ended.total")
                .description("총 경매 종료 수")
                .tag("result", "success")
                .register(registry);

        endedNoBidder = Counter.builder("auction.ended.total")
                .description("낙찰 혹은 유찰 경매 수")
                .tag("result", "no_bidder")
                .register(registry);
    }

    public void incrementRegistered()    { registered.increment(); }
    public void incrementEndedSuccess()  { endedSuccess.increment(); }
    public void incrementEndedNoBidder() { endedNoBidder.increment(); }
}
