package com.rocketcrew.pocat.global.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RedisRateLimiter {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String RATE_LIMIT_SCRIPT =
            "local count = redis.call('INCR', KEYS[1])\n" +
            "if tonumber(count) == 1 then\n" +
            "  redis.call('EXPIRE', KEYS[1], ARGV[2])\n" +
            "end\n" +
            "if tonumber(count) > tonumber(ARGV[1]) then\n" +
            "  return 1\n" +
            "end\n" +
            "return 0";

    public boolean isAllowed(String key, int limit, long windowSeconds) {
        Long result = stringRedisTemplate.execute(
                new DefaultRedisScript<>(RATE_LIMIT_SCRIPT, Long.class),
                List.of(key),
                String.valueOf(limit),
                String.valueOf(windowSeconds)
        );
        return result == null || result == 0L;
    }
}
