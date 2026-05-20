package com.rocketcrew.pocat.global.scheduler;

import com.rocketcrew.pocat.domain.community.freepost.repository.FreePostRepository;
import com.rocketcrew.pocat.domain.community.tradepost.repository.TradePostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViewCountFlushScheduler {

    private final StringRedisTemplate redisTemplate;
    private final TradePostRepository tradePostRepository;
    private final FreePostRepository freePostRepository;

    @Autowired
    @Lazy
    private ViewCountFlushScheduler self;

    private static final String TRADE_BUFFER_KEY = "view:buffer";
    private static final String TRADE_PROCESSING_KEY = "view:buffer:processing";
    private static final String FREE_BUFFER_KEY = "view:free:buffer";
    private static final String FREE_PROCESSING_KEY = "view:free:buffer:processing";
    private static final String FREE_COMMENT_BUFFER_KEY = "comment:free:buffer";
    private static final String FREE_COMMENT_PROCESSING_KEY = "comment:free:buffer:processing";

    private enum RepositoryType {
        TRADE_VIEW,
        FREE_VIEW,
        FREE_COMMENT
    }

    @Scheduled(fixedDelay = 60_000)
    public void flush() {
        try { self.flushTrade(); } catch (Exception e) { log.error("flushTrade failed", e); }
        try { self.flushFreeView(); } catch (Exception e) { log.error("flushFreeView failed", e); }
        try { self.flushFreeComment(); } catch (Exception e) { log.error("flushFreeComment failed", e); }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void flushTrade() {
        flushBuffer(TRADE_PROCESSING_KEY, TRADE_BUFFER_KEY, RepositoryType.TRADE_VIEW);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void flushFreeView() {
        flushBuffer(FREE_PROCESSING_KEY, FREE_BUFFER_KEY, RepositoryType.FREE_VIEW);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void flushFreeComment() {
        flushBuffer(FREE_COMMENT_PROCESSING_KEY, FREE_COMMENT_BUFFER_KEY, RepositoryType.FREE_COMMENT);
    }

    private void flushBuffer(String processingKey, String bufferKey, RepositoryType type) {
        // 이전 사이클에서 실패한 항목을 버퍼에 재병합
        String failedKey = processingKey + ":failed";
        if (redisTemplate.hasKey(failedKey)) {
            log.warn("이전 실패 항목 발견, 버퍼에 재병합합니다. key={}", failedKey);
            Set<ZSetOperations.TypedTuple<String>> failedEntries =
                    redisTemplate.opsForZSet().rangeWithScores(failedKey, 0, -1);
            if (failedEntries != null) {
                for (ZSetOperations.TypedTuple<String> entry : failedEntries) {
                    redisTemplate.opsForZSet().incrementScore(bufferKey, entry.getValue(), entry.getScore());
                }
            }
            redisTemplate.delete(failedKey);
        }

        if (redisTemplate.hasKey(processingKey)) {
            log.warn("진행이 안된 데이터 발견, DB업데이트를 재 시도합니다. key={}", processingKey);
            flushKey(processingKey, type);
        }

        if (!redisTemplate.hasKey(bufferKey)) {
            return;
        }

        redisTemplate.rename(bufferKey, processingKey);
        flushKey(processingKey, type);
    }

    private void flushKey(String key, RepositoryType type) {
        Set<ZSetOperations.TypedTuple<String>> entries =
                redisTemplate.opsForZSet().rangeWithScores(key, 0, -1);

        if (entries == null || entries.isEmpty()) {
            redisTemplate.delete(key);
            return;
        }

        String failedKey = key + ":failed";

        for (ZSetOperations.TypedTuple<String> entry : entries) {
            try {
                Long postId = Long.parseLong(entry.getValue());
                int count = entry.getScore().intValue();
                switch (type) {
                    case FREE_VIEW -> freePostRepository.increaseViewCount(postId, count);
                    case TRADE_VIEW -> tradePostRepository.increaseViewCount(postId, count);
                    case FREE_COMMENT -> freePostRepository.updateCommentCount(postId, count);
                }
            } catch (Exception e) {
                log.error("flush failed for entry: {}, key={}, failedKey={}", entry.getValue(), key, failedKey, e);
                redisTemplate.opsForZSet().incrementScore(failedKey, entry.getValue(), entry.getScore());
                redisTemplate.expire(failedKey, 24, java.util.concurrent.TimeUnit.HOURS);
            }
        }

        redisTemplate.delete(key);
    }
}
