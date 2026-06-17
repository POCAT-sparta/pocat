package com.rocketcrew.pocat.domain.auction.redis;

import jakarta.annotation.PostConstruct;
import com.rocketcrew.pocat.domain.auction.service.AuctionLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionExpirationRedisSubscriber {

    private final AuctionLifecycleService auctionLifecycleService;
    private final AuctionExpirationRedisService auctionExpirationRedisService;
    private final RedissonClient redissonClient;

    @PostConstruct
    public void subscribe() {
        var topic = redissonClient.getPatternTopic("__keyevent@*__:expired", StringCodec.INSTANCE);
        if (topic == null) return;
        topic.addListener(String.class, (pattern, channel, expiredKey) -> {
            if (!auctionExpirationRedisService.isAuctionEndKey(expiredKey)) return;
            try {
                Long auctionId = auctionExpirationRedisService.parseAuctionId(expiredKey);
                auctionLifecycleService.closeExpiredAuction(auctionId);
            } catch (Exception e) {
                log.warn("Redis 만료 이벤트 기반 경매 종료 처리 실패: key={}", expiredKey, e);
            }
        });
    }
}
