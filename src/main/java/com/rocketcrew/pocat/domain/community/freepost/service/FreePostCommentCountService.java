package com.rocketcrew.pocat.domain.community.freepost.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FreePostCommentCountService {

    private final StringRedisTemplate redisTemplate;

    static final String BUFFER_KEY = "comment:free:buffer";

    public void increment(Long freePostId) {
        try {
            redisTemplate.opsForZSet().incrementScore(BUFFER_KEY, freePostId.toString(), 1);
        } catch (Exception e) {
            log.warn("comment count increment skipped. freePostId={}", freePostId, e);
        }
    }

    public void decrement(Long freePostId) {
        try {
            redisTemplate.opsForZSet().incrementScore(BUFFER_KEY, freePostId.toString(), -1);
        } catch (Exception e) {
            log.warn("comment count decrement skipped. freePostId={}", freePostId, e);
        }
    }
}
