package com.rocketcrew.pocat.global.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OrderMetrics {

    private final Counter createdFromAuction;
    private final Counter createdFromBuyout;
    private final Counter cancelled;

    public OrderMetrics(MeterRegistry registry) {
        createdFromAuction = Counter.builder("order.created.total")
                .tag("type", "auction")
                .description("자동결제 주문 생성 수")
                .register(registry);

        createdFromBuyout  = Counter.builder("order.created.total")
                .tag("type", "buyout")
                .description("buyout 주문 생성 수")
                .register(registry);

        cancelled          = Counter.builder("order.cancelled.total")
                .description("취소 주문 수")
                .register(registry);
    }

    public void incrementCreatedFromAuction() { createdFromAuction.increment(); }
    public void incrementCancelled()          { cancelled.increment(); }
}
