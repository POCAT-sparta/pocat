package com.rocketcrew.pocat.domain.community.freepost.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostCommentCacheEvictor {

    private static final String KEY_PATTERN_PREFIX = "post:comments::";

    private final StringRedisTemplate stringRedisTemplate;

    public void evictAll(Long postId) {
        String pattern = KEY_PATTERN_PREFIX + postId + ":page:*";
        List<String> keysToDelete = new ArrayList<>();

        try (Cursor<String> cursor = stringRedisTemplate.scan(
                ScanOptions.scanOptions().match(pattern).count(100).build())) {
            while (cursor.hasNext()) {
                keysToDelete.add(cursor.next());
            }
        } catch (Exception ex) {
            log.warn("[CACHE] 캐시 SCAN 실패 pattern={}: {}", pattern, ex.getMessage());
            return;
        }

        if (!keysToDelete.isEmpty()) {
            stringRedisTemplate.delete(keysToDelete);
            log.debug("[CACHE] 댓글 캐시 삭제 완료 evictedKeys={} postId={}", keysToDelete.size(), postId);
        }
    }

    public void evictAfterCommit(Long postId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictAll(postId);
                }
            });
        } else {
            evictAll(postId);
        }
    }
}
