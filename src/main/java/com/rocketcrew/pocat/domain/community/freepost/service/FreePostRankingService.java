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

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FreePostRankingService {

    private final StringRedisTemplate redisTemplate;
    private final FreePostRepository freePostRepository;
    private final UserRepository userRepository;

    static final String RANKING_KEY = "{ranking:free}:popular";
    private static final int RANKING_TTL_SECONDS = 70;
    private static final int COMMENT_WEIGHT = FreePostRepository.COMMENT_WEIGHT;
    private static final int POPULAR_DAYS = 7;

    public List<FreePostResponse> getPopular(int size) {
        int clampedSize = Math.min(Math.max(size, 1), 50);
        Set<ZSetOperations.TypedTuple<String>> entries =
                redisTemplate.opsForZSet().reverseRangeWithScores(RANKING_KEY, 0, clampedSize - 1);

        if (entries == null || entries.isEmpty()) {
            return fallbackFromDb(clampedSize);
        }

        List<Long> postIds = entries.stream()
                .filter(e -> e.getValue() != null)
                .flatMap(e -> {
                    try {
                        return Stream.of(Long.parseLong(e.getValue()));
                    } catch (NumberFormatException ex) {
                        log.warn("invalid ranking entry in Redis, skipping: value={}", e.getValue());
                        return Stream.empty();
                    }
                })
                .collect(Collectors.toList());

        if (postIds.isEmpty()) {
            return fallbackFromDb(clampedSize);
        }

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
        List<FreePost> posts = freePostRepository.findTopByPopularScore(
                PageRequest.of(0, size), LocalDateTime.now().minusDays(POPULAR_DAYS));
        List<Long> userIds = posts.stream().map(FreePost::getUserId).distinct().toList();
        Map<Long, String> nicknameMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));
        return posts.stream()
                .map(p -> FreePostResponse.of(p, nicknameMap.getOrDefault(p.getUserId(), ""), p.getCommentCount()))
                .collect(Collectors.toList());
    }

    public void refreshRanking() {
        try {
            List<FreePost> posts = freePostRepository.findTopByPopularScore(
                    PageRequest.of(0, 100), LocalDateTime.now().minusDays(POPULAR_DAYS));
            if (posts.isEmpty()) return;

            String newKey = RANKING_KEY + ":new";
            redisTemplate.delete(newKey);

            for (FreePost post : posts) {
                double score = post.getViewCount() + (double) post.getCommentCount() * COMMENT_WEIGHT;
                redisTemplate.opsForZSet().add(newKey, post.getId().toString(), score);
            }

            redisTemplate.rename(newKey, RANKING_KEY);
            redisTemplate.expire(RANKING_KEY, RANKING_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("ranking refresh failed", e);
        }
    }
}
