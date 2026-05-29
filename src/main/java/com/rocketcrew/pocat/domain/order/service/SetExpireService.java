package com.rocketcrew.pocat.domain.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SetExpireService {

    static final String PAYMENT_EXPIRY_KEY_PREFIX = "order:expire";
    static final String PAYMENT_SHADOW_KEY_PREFIX = "order:shadow";

    private final StringRedisTemplate redisTemplate;

    public void scheduleExpiry(Long orderId, Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) return;

        redisTemplate.opsForValue().setIfAbsent(
                PAYMENT_EXPIRY_KEY_PREFIX + orderId,
                String.valueOf(orderId),
                ttl
        );
        markAsProcessing(orderId);
    }

    private void markAsProcessing(Long orderId) {
        redisTemplate.opsForValue().setIfAbsent(PAYMENT_SHADOW_KEY_PREFIX + orderId, String.valueOf(orderId));
    }

    public void cancelExpiry(Long orderId) {
        redisTemplate.delete(PAYMENT_EXPIRY_KEY_PREFIX + orderId);
        redisTemplate.delete(PAYMENT_SHADOW_KEY_PREFIX + orderId);
    }
}
