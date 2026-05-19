package com.rocketcrew.pocat.global.scheduler;

import com.rocketcrew.pocat.domain.community.tradepost.repository.TradePostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViewCountFlushScheduler {

    private final StringRedisTemplate redisTemplate;
    private final TradePostRepository tradePostRepository;

    private static final String BUFFER_KEY = "view:buffer";
    private static final String PROCESSING_KEY = "view:buffer:processing";

    @Transactional
    @Scheduled(fixedDelay = 60_000)
    public void flush() {
        if (redisTemplate.hasKey(PROCESSING_KEY)) {
            log.warn("진행이 안된 데이터 발견, DB업데이트를 재 시도합니다.");
            flushKey(PROCESSING_KEY);
        }

        if (!redisTemplate.hasKey(BUFFER_KEY)) {
            return;
        }

        redisTemplate.rename(BUFFER_KEY, PROCESSING_KEY);
        flushKey(PROCESSING_KEY);
    }

    private void flushKey(String key) {
        Set<ZSetOperations.TypedTuple<String>> entries =
                redisTemplate.opsForZSet().rangeWithScores(key, 0, -1);

        if (entries == null || entries.isEmpty()) {
            redisTemplate.delete(key);
            return;
        }

        for (ZSetOperations.TypedTuple<String> entry : entries) {
            try {
                Long tradePostId = Long.parseLong(entry.getValue());
                int count = entry.getScore().intValue();
                tradePostRepository.increaseViewCount(tradePostId, count);
            } catch (Exception e) {
                log.error("view count flush failed for entry: {}", entry.getValue(), e);
            }
        }

        redisTemplate.delete(key);
    }
}
