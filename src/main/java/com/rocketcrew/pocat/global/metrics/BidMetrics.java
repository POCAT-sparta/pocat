package com.rocketcrew.pocat.global.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class BidMetrics {

    private final Counter created;

    public BidMetrics(MeterRegistry registry) {
        created = Counter.builder("bid.created.total")
                .description("bid 생성 수")
                .register(registry);
    }

    public void incrementCreated() { created.increment(); }
}
