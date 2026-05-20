package com.rocketcrew.pocat.global.scheduler;

import com.rocketcrew.pocat.domain.community.freepost.repository.FreePostRepository;
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
    private final FreePostRepository freePostRepository;

    private static final String TRADE_BUFFER_KEY = "view:buffer";
    private static final String TRADE_PROCESSING_KEY = "view:buffer:processing";
    private static final String FREE_BUFFER_KEY = "view:free:buffer";
    private static final String FREE_PROCESSING_KEY = "view:free:buffer:processing";

    @Transactional
    @Scheduled(fixedDelay = 60_000)
    public void flush() {
        flushBuffer(TRADE_PROCESSING_KEY, TRADE_BUFFER_KEY, false);
        flushBuffer(FREE_PROCESSING_KEY, FREE_BUFFER_KEY, true);
    }

    private void flushBuffer(String processingKey, String bufferKey, boolean isFreePost) {
        if (redisTemplate.hasKey(processingKey)) {
            log.warn("진행이 안된 데이터 발견, DB업데이트를 재 시도합니다. key={}", processingKey);
            flushKey(processingKey, isFreePost);
        }

        if (!redisTemplate.hasKey(bufferKey)) {
            return;
        }

        redisTemplate.rename(bufferKey, processingKey);
        flushKey(processingKey, isFreePost);
    }

    private void flushKey(String key, boolean isFreePost) {
        Set<ZSetOperations.TypedTuple<String>> entries =
                redisTemplate.opsForZSet().rangeWithScores(key, 0, -1);

        if (entries == null || entries.isEmpty()) {
            redisTemplate.delete(key);
            return;
        }

        for (ZSetOperations.TypedTuple<String> entry : entries) {
            try {
                Long postId = Long.parseLong(entry.getValue());
                int count = entry.getScore().intValue();
                if (isFreePost) {
                    freePostRepository.increaseViewCount(postId, count);
                } else {
                    tradePostRepository.increaseViewCount(postId, count);
                }
            } catch (Exception e) {
                log.error("view count flush failed for entry: {}, key={}", entry.getValue(), key, e);
            }
        }

        redisTemplate.delete(key);
    }
}
