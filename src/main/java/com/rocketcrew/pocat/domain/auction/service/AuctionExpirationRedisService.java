package com.rocketcrew.pocat.domain.auction.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionExpirationRedisService {

    private static final String END_KEY_PREFIX = "auction:end:";
    private static final String SHADOW_KEY_PREFIX = "auction:end:shadow:";

    private final StringRedisTemplate redisTemplate;

    // 경매 종료 시각에 맞춰 만료 이벤트용 TTL 키와 미처리 추적용 shadow key를 저장한다.
    public void setExpirationKeys(Long auctionId, LocalDateTime endedAt) {
        if (endedAt == null) {
            log.warn("경매 종료 TTL 설정 생략: endedAt 없음, auctionId={}", auctionId);
            return;
        }

        long ttlSeconds = Duration.between(LocalDateTime.now(), endedAt).getSeconds();
        if (ttlSeconds <= 0) {
            log.warn("경매 종료 TTL 설정 생략: 이미 종료 시각 경과, auctionId={}, endedAt={}", auctionId, endedAt);
            return;
        }

        String auctionIdValue = String.valueOf(auctionId);
        redisTemplate.opsForValue().set(endKey(auctionId), auctionIdValue, Duration.ofSeconds(ttlSeconds));
        redisTemplate.opsForValue().set(shadowKey(auctionId), auctionIdValue);
    }

    // Redis 만료 이벤트에서 받은 key가 경매 종료 TTL 키인지 확인한다.
    public boolean isAuctionEndKey(String key) {
        return key != null && key.startsWith(END_KEY_PREFIX);
    }

    // 경매 종료 TTL 키에서 auctionId를 추출한다.
    public Long parseAuctionId(String key) {
        if (!isAuctionEndKey(key)) {
            throw new IllegalArgumentException("경매 종료 키 형식이 아닙니다: " + key);
        }
        return Long.parseLong(key.substring(END_KEY_PREFIX.length()));
    }

    // 종료 처리가 성공한 경매의 shadow key를 삭제한다.
    public void deleteShadowKey(Long auctionId) {
        redisTemplate.delete(shadowKey(auctionId));
    }

    // Redis TTL 만료 이벤트를 발생시키는 경매 종료 키를 만든다.
    private String endKey(Long auctionId) {
        return END_KEY_PREFIX + auctionId;
    }

    // 종료 처리 성공 전까지 남겨둘 shadow key를 만든다.
    private String shadowKey(Long auctionId) {
        return SHADOW_KEY_PREFIX + auctionId;
    }
}
