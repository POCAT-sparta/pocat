package com.rocketcrew.pocat.domain.community.freepost.service;

import com.rocketcrew.pocat.domain.community.freepost.dto.response.FreePostResponse;
import com.rocketcrew.pocat.domain.community.freepost.entity.FreePost;
import com.rocketcrew.pocat.domain.community.freepost.repository.FreePostRepository;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FreePostRankingService {

    private final StringRedisTemplate redisTemplate;
    private final FreePostRepository freePostRepository;
    private final UserRepository userRepository;

    static final String RANKING_KEY = "ranking:free:popular";
    private static final int RANKING_TTL_SECONDS = 70;
    private static final int LIKE_WEIGHT = 3;

    public List<FreePostResponse> getPopular(int size) {
        int clampedSize = Math.min(Math.max(size, 1), 50);
        Set<ZSetOperations.TypedTuple<String>> entries =
                redisTemplate.opsForZSet().reverseRangeWithScores(RANKING_KEY, 0, clampedSize - 1);

        if (entries == null || entries.isEmpty()) {
            return fallbackFromDb(clampedSize);
        }

        List<Long> postIds = entries.stream()
                .map(e -> Long.parseLong(e.getValue()))
                .collect(Collectors.toList());

        Map<Long, FreePost> postMap = freePostRepository.findAllById(postIds).stream()
                .collect(Collectors.toMap(FreePost::getId, p -> p));

        List<Long> userIds = postMap.values().stream().map(FreePost::getUserId).distinct().toList();
        Map<Long, String> nicknameMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));

        return postIds.stream()
                .filter(postMap::containsKey)
                .map(id -> {
                    FreePost p = postMap.get(id);
                    return FreePostResponse.of(p, nicknameMap.getOrDefault(p.getUserId(), ""), p.getCommentCount());
                })
                .collect(Collectors.toList());
    }

    private List<FreePostResponse> fallbackFromDb(int size) {
        return freePostRepository.findTopByPopularScore(PageRequest.of(0, size)).stream()
                .map(p -> {
                    String nickname = userRepository.findById(p.getUserId())
                            .map(User::getNickname).orElse("");
                    return FreePostResponse.of(p, nickname, p.getCommentCount());
                })
                .collect(Collectors.toList());
    }

    public void refreshRanking() {
        try {
            List<FreePost> posts = freePostRepository.findTopByPopularScore(PageRequest.of(0, 100));
            if (posts.isEmpty()) return;

            String newKey = RANKING_KEY + ":new";
            redisTemplate.delete(newKey);

            for (FreePost post : posts) {
                double score = post.getViewCount() + (double) post.getCommentCount() * LIKE_WEIGHT;
                redisTemplate.opsForZSet().add(newKey, post.getId().toString(), score);
            }

            redisTemplate.rename(newKey, RANKING_KEY);
            redisTemplate.expire(RANKING_KEY, RANKING_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("ranking refresh failed", e);
        }
    }
}
