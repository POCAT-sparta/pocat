package com.rocketcrew.pocat.domain.auction.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionExpirationRedisSubscriber implements MessageListener {

    private final AuctionLifecycleService auctionLifecycleService;
    private final AuctionExpirationRedisService auctionExpirationRedisService;

    // Redis key 만료 이벤트 중 경매 종료 TTL 키만 골라 경매 종료 처리를 수행한다.
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = new String(message.getBody(), StandardCharsets.UTF_8);
        if (!auctionExpirationRedisService.isAuctionEndKey(expiredKey)) {
            return;
        }

        try {
            Long auctionId = auctionExpirationRedisService.parseAuctionId(expiredKey);
            auctionLifecycleService.closeExpiredAuction(auctionId);
        } catch (Exception e) {
            log.warn("Redis 만료 이벤트 기반 경매 종료 처리 실패: key={}", expiredKey, e);
        }
    }
}
