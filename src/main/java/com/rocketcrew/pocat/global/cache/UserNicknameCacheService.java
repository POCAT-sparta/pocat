package com.rocketcrew.pocat.global.cache;

import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserNicknameCacheService {

    private static final String KEY_PREFIX = "user:nickname::";
    private static final Duration TTL = Duration.ofMinutes(60);

    private final StringRedisTemplate stringRedisTemplate;
    private final UserRepository userRepository;

    public Map<Long, String> getNicknames(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        List<String> keys = userIds.stream()
                .map(id -> KEY_PREFIX + id)
                .toList();

        List<String> values;
        try {
            List<Object> pipelined = stringRedisTemplate.executePipelined(
                    (RedisCallback<Object>) connection -> {
                        for (String key : keys) {
                            connection.stringCommands().get(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        }
                        return null;
                    }
            );
            values = pipelined.stream()
                    .map(v -> v instanceof String ? (String) v : null)
                    .toList();
        } catch (Exception e) {
            values = null;
        }

        Map<Long, String> result = new HashMap<>();
        List<Long> missedIds = new ArrayList<>();

        for (int i = 0; i < userIds.size(); i++) {
            String value = (values != null) ? values.get(i) : null;
            if (value != null) {
                result.put(userIds.get(i), value);
            } else {
                missedIds.add(userIds.get(i));
            }
        }

        if (!missedIds.isEmpty()) {
            List<User> dbUsers = userRepository.findAllById(missedIds);
            for (User user : dbUsers) {
                result.put(user.getId(), user.getNickname());
                try {
                    stringRedisTemplate.opsForValue().set(KEY_PREFIX + user.getId(), user.getNickname(), TTL);
                } catch (Exception ignored) {
                }
            }
        }

        return result;
    }

    public void evict(Long userId) {
        try {
            stringRedisTemplate.delete(KEY_PREFIX + userId);
        } catch (Exception ignored) {
        }
    }
}
