package com.rocketcrew.pocat.domain.community.tradepost.service;

import com.rocketcrew.pocat.global.util.IpHashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class ViewCountService {

    private final StringRedisTemplate redisTemplate;
    private final IpHashUtils ipHashUtils;

    private static final String DEDUP_KEY_PREFIX = "view:dedup:";
    private static final String BUFFER_KEY = "view:buffer";
    private static final long DEDUP_TTL_MINUTES = 10;

    public void increaseViewCount(Long tradePostId, String clientIp) {
        try {
            String dedupKey = DEDUP_KEY_PREFIX + tradePostId + ":" + ipHashUtils.hash(clientIp);

            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(dedupKey, "1", DEDUP_TTL_MINUTES, TimeUnit.MINUTES);

            if (Boolean.TRUE.equals(isNew)) {
                redisTemplate.opsForZSet().incrementScore(BUFFER_KEY, tradePostId.toString(), 1);
            }
        }catch (Exception e) {
            log.warn("view count capture skipped. tradePostId={}", tradePostId, e);
        }

    }
}
