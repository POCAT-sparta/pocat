package com.rocketcrew.pocat.domain.auction.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionExpirationRedisService {

    private static final String END_KEY_PREFIX = "auction:end:";
    private static final String SHADOW_KEY_PREFIX = "auction:end:shadow:";
    private static final Pattern END_KEY_PATTERN = Pattern.compile("^" + Pattern.quote(END_KEY_PREFIX) + "\\d+$");
    private final StringRedisTemplate redisTemplate;

    public void setExpirationKeys(Long auctionId, LocalDateTime endedAt) {
        if (endedAt == null) {
            log.warn("Auction expiration key skipped. endedAt is null. auctionId={}", auctionId);
            return;
        }

        long ttlSeconds = Duration.between(LocalDateTime.now(ZoneOffset.UTC), endedAt).getSeconds();
        if (ttlSeconds <= 0) {
            log.warn("Auction expiration key skipped. endedAt already passed. auctionId={}, endedAt={}",
                    auctionId, endedAt);
            return;
        }

        String auctionIdValue = String.valueOf(auctionId);
        redisTemplate.opsForValue().set(endKey(auctionId), auctionIdValue, Duration.ofSeconds(ttlSeconds));
        redisTemplate.opsForValue().set(shadowKey(auctionId), auctionIdValue);
    }

    public boolean isAuctionEndKey(String key) {
        return key != null && END_KEY_PATTERN.matcher(key).matches();
    }

    public Long parseAuctionId(String key) {
        if (!isAuctionEndKey(key)) {
            throw new IllegalArgumentException("Invalid auction expiration key: " + key);
        }
        return Long.parseLong(key.substring(END_KEY_PREFIX.length()));
    }

    public void deleteExpirationKeys(Long auctionId) {
        redisTemplate.delete(endKey(auctionId));
        redisTemplate.delete(shadowKey(auctionId));
    }

    private String endKey(Long auctionId) {
        return END_KEY_PREFIX + auctionId;
    }

    private String shadowKey(Long auctionId) {
        return SHADOW_KEY_PREFIX + auctionId;
    }
}
