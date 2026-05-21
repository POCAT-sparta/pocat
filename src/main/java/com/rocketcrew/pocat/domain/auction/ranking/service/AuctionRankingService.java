package com.rocketcrew.pocat.domain.auction.ranking.service;

import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.ranking.config.AuctionRankingProperties;
import com.rocketcrew.pocat.domain.auction.ranking.dto.AuctionCountProjection;
import com.rocketcrew.pocat.domain.auction.ranking.dto.response.PopularAuctionResponse;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.bid.repository.AuctionBidRepository;
import com.rocketcrew.pocat.domain.like.repository.LikeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionRankingService {

    private final StringRedisTemplate redisTemplate;
    private final AuctionRepository auctionRepository;
    private final LikeRepository likeRepository;
    private final AuctionBidRepository auctionBidRepository;
    private final AuctionRankingProperties properties;

    static final String RANKING_KEY = "ranking:auction:popular";

    public List<PopularAuctionResponse> getPopular(int size) {
        int clampedSize = Math.min(Math.max(size, 1), properties.getMaxResponseSize());

        Set<ZSetOperations.TypedTuple<String>> entries;
        try {
            entries = redisTemplate.opsForZSet().reverseRangeWithScores(RANKING_KEY, 0, clampedSize - 1);
        } catch (Exception e) {
            log.warn("Redis unavailable in getPopular — falling back to DB", e);
            return fallbackFromDb(clampedSize);
        }

        if (entries == null || entries.isEmpty()) {
            log.info("Auction ranking cache miss — falling back to DB");
            return fallbackFromDb(clampedSize);
        }

        List<Long> auctionIds = entries.stream()
                .filter(e -> e.getValue() != null && e.getScore() != null && e.getScore() > 0)
                .flatMap(e -> {
                    try {
                        return Stream.of(Long.parseLong(e.getValue()));
                    } catch (NumberFormatException ex) {
                        log.warn("Invalid ranking entry in Redis, skipping: value={}", e.getValue());
                        return Stream.empty();
                    }
                })
                .collect(Collectors.toList());

        if (auctionIds.isEmpty()) {
            return fallbackFromDb(clampedSize);
        }

        Map<Long, Auction> auctionMap = auctionRepository.findAllById(auctionIds).stream()
                .collect(Collectors.toMap(Auction::getId, a -> a));

        Map<Long, Long> likeCounts = toLongMap(likeRepository.countByAuctionIdIn(auctionIds));
        Map<Long, Long> bidCounts = toLongMap(auctionBidRepository.countByAuctionIdIn(auctionIds));

        return auctionIds.stream()
                .filter(auctionMap::containsKey)
                .map(id -> {
                    Auction a = auctionMap.get(id);
                    long likeCount = likeCounts.getOrDefault(id, 0L);
                    long bidCount = bidCounts.getOrDefault(id, 0L);
                    double score = likeCount * properties.getLikeWeight() + bidCount * properties.getBidWeight();
                    return PopularAuctionResponse.of(a, likeCount, bidCount, score);
                })
                .collect(Collectors.toList());
    }

    public void refreshRanking() {
        try {
            List<Auction> activeAuctions = auctionRepository.findAllByStatus(AuctionStatus.ACTIVE);
            if (activeAuctions.isEmpty()) {
                log.debug("No active auctions — skipping ranking refresh");
                return;
            }

            List<Long> ids = activeAuctions.stream().map(Auction::getId).toList();

            Map<Long, Long> likeCounts = toLongMap(likeRepository.countByAuctionIdIn(ids));
            Map<Long, Long> bidCounts = toLongMap(auctionBidRepository.countByAuctionIdIn(ids));

            String newKey = RANKING_KEY + ":new";
            redisTemplate.delete(newKey);

            int added = 0;
            for (Auction auction : activeAuctions) {
                long likeCount = likeCounts.getOrDefault(auction.getId(), 0L);
                long bidCount = bidCounts.getOrDefault(auction.getId(), 0L);
                double score = likeCount * properties.getLikeWeight() + bidCount * properties.getBidWeight();
                if (score > 0) {
                    redisTemplate.opsForZSet().add(newKey, auction.getId().toString(), score);
                    added++;
                }
            }

            if (added == 0) {
                log.debug("No auctions with score > 0 — keeping existing ranking");
                return;
            }

            redisTemplate.rename(newKey, RANKING_KEY);
            redisTemplate.expire(RANKING_KEY, properties.getTtlSeconds(), TimeUnit.SECONDS);
            log.debug("Auction ranking refreshed: {} auctions", added);

        } catch (Exception e) {
            log.warn("Auction ranking refresh failed", e);
        }
    }

    private List<PopularAuctionResponse> fallbackFromDb(int size) {
        List<Auction> activeAuctions = auctionRepository.findAllByStatus(AuctionStatus.ACTIVE);
        if (activeAuctions.isEmpty()) return Collections.emptyList();

        List<Long> ids = activeAuctions.stream().map(Auction::getId).toList();
        Map<Long, Long> likeCounts = toLongMap(likeRepository.countByAuctionIdIn(ids));
        Map<Long, Long> bidCounts = toLongMap(auctionBidRepository.countByAuctionIdIn(ids));

        return activeAuctions.stream()
                .map(a -> {
                    long likeCount = likeCounts.getOrDefault(a.getId(), 0L);
                    long bidCount = bidCounts.getOrDefault(a.getId(), 0L);
                    double score = likeCount * properties.getLikeWeight() + bidCount * properties.getBidWeight();
                    return PopularAuctionResponse.of(a, likeCount, bidCount, score);
                })
                .filter(r -> r.popularityScore() > 0)
                .sorted(Comparator.comparingDouble(PopularAuctionResponse::popularityScore).reversed())
                .limit(size)
                .collect(Collectors.toList());
    }

    private Map<Long, Long> toLongMap(List<AuctionCountProjection> projections) {
        return projections.stream()
                .collect(Collectors.toMap(AuctionCountProjection::getAuctionId, AuctionCountProjection::getCnt));
    }
}
