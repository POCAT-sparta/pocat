package com.rocketcrew.pocat.domain.community.freepost.service;

import com.rocketcrew.pocat.global.util.IpHashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FreePostViewCountService {

    private final StringRedisTemplate redisTemplate;
    private final IpHashUtils ipHashUtils;

    static final String DEDUP_KEY_PREFIX = "view:free:dedup:";
    static final String BUFFER_KEY = "view:free:buffer";
    private static final long DEDUP_TTL_MINUTES = 10;

    public void increaseViewCount(Long freePostId, String clientIp) {
        try {
            String dedupKey = DEDUP_KEY_PREFIX + freePostId + ":" + ipHashUtils.hash(clientIp);

            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(dedupKey, "1", DEDUP_TTL_MINUTES, TimeUnit.MINUTES);

            if (Boolean.TRUE.equals(isNew)) {
                redisTemplate.opsForZSet().incrementScore(BUFFER_KEY, freePostId.toString(), 1);
            }
        } catch (Exception e) {
            log.warn("view count capture skipped. freePostId={}", freePostId, e);
        }
    }
}
