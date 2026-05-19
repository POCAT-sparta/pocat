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
        if (!redisTemplate.hasKey(BUFFER_KEY)) {
            return;
        }
        redisTemplate.rename(BUFFER_KEY, PROCESSING_KEY);

        Set<ZSetOperations.TypedTuple<String>> entries =
                redisTemplate.opsForZSet().rangeWithScores(PROCESSING_KEY, 0, -1);

        if (entries == null || entries.isEmpty()) {
            redisTemplate.delete(PROCESSING_KEY);
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

        redisTemplate.delete(PROCESSING_KEY);
    }
}
