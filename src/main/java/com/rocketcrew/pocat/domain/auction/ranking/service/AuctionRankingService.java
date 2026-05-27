package com.rocketcrew.pocat.domain.auction.ranking.service;

import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.dto.response.SearchAuctionResponse;
import com.rocketcrew.pocat.domain.auction.ranking.config.AuctionRankingProperties;
import com.rocketcrew.pocat.domain.auction.ranking.dto.AuctionCountProjection;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.bid.repository.AuctionBidRepository;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.service.CardQueryService;
import com.rocketcrew.pocat.domain.like.repository.LikeRepository;
import com.rocketcrew.pocat.domain.user.service.UserQueryService;
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
    private final CardQueryService cardQueryService;
    private final UserQueryService userQueryService;
    private final AuctionRankingProperties properties;

    static final String RANKING_KEY = "ranking:auction:popular";

    public List<SearchAuctionResponse> getPopular(int size) {
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
        Map<Long, Card> cardMap = loadCards(auctionMap.values().stream().map(Auction::getCardId).distinct().toList());
        Map<Long, String> sellerNicknames = loadSellerNicknames(auctionMap.values().stream()
                .map(Auction::getSellerId)
                .distinct()
                .toList());

        return auctionIds.stream()
                .filter(auctionMap::containsKey)
                .map(id -> {
                    Auction a = auctionMap.get(id);
                    if (a.getStatus() != AuctionStatus.ACTIVE) {
                        return null;
                    }
                    Card card = cardMap.get(a.getCardId());
                    return toSearchAuctionResponse(a, card, sellerNicknames.get(a.getSellerId()));
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public void refreshRanking() {
        try {
            List<Auction> activeAuctions = auctionRepository.findAllByStatus(AuctionStatus.ACTIVE);
            if (activeAuctions.isEmpty()) {
                redisTemplate.delete(RANKING_KEY);
                log.debug("No active auctions — cleared stale ranking");
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
                redisTemplate.delete(RANKING_KEY);
                log.debug("No auctions with score > 0 — cleared stale ranking");
                return;
            }

            redisTemplate.rename(newKey, RANKING_KEY);
            redisTemplate.expire(RANKING_KEY, properties.getTtlSeconds(), TimeUnit.SECONDS);
            log.debug("Auction ranking refreshed: {} auctions", added);

        } catch (Exception e) {
            log.warn("Auction ranking refresh failed", e);
        }
    }

    private List<SearchAuctionResponse> fallbackFromDb(int size) {
        List<Auction> activeAuctions = auctionRepository.findAllByStatus(AuctionStatus.ACTIVE);
        if (activeAuctions.isEmpty()) return Collections.emptyList();

        List<Long> ids = activeAuctions.stream().map(Auction::getId).toList();
        Map<Long, Card> cardMap = loadCards(activeAuctions.stream()
                .map(Auction::getCardId)
                .distinct()
                .toList());
        Map<Long, String> sellerNicknames = loadSellerNicknames(activeAuctions.stream()
                .map(Auction::getSellerId)
                .distinct()
                .toList());
        Map<Long, Long> likeCounts = toLongMap(likeRepository.countByAuctionIdIn(ids));
        Map<Long, Long> bidCounts = toLongMap(auctionBidRepository.countByAuctionIdIn(ids));

        return activeAuctions.stream()
                .map(a -> {
                    Card card = cardMap.get(a.getCardId());
                    long bidCount = bidCounts.getOrDefault(a.getId(), 0L);
                    long likeCount = likeCounts.getOrDefault(a.getId(), 0L);
                    double score = likeCount * properties.getLikeWeight() + bidCount * properties.getBidWeight();
                    SearchAuctionResponse response = toSearchAuctionResponse(
                            a, card, sellerNicknames.get(a.getSellerId()));
                    return new PopularAuctionItem(response, score);
                })
                .filter(item -> item.popularityScore() > 0)
                .sorted(Comparator.comparingDouble(PopularAuctionItem::popularityScore).reversed())
                .limit(size)
                .map(PopularAuctionItem::response)
                .collect(Collectors.toList());
    }

    private Map<Long, Card> loadCards(List<Long> cardIds) {
        if (cardIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return cardQueryService.getCardEntities(cardIds);
    }

    private Map<Long, String> loadSellerNicknames(List<Long> sellerIds) {
        if (sellerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userQueryService.getNicknamesByUserIds(sellerIds);
    }

    private SearchAuctionResponse toSearchAuctionResponse(
            Auction auction,
            Card card,
            String sellerNickname
    ) {
        return new SearchAuctionResponse(
                auction.getId(),
                auction.getSellerId(),
                sellerNickname,
                auction.getTitle(),
                auction.getCardId(),
                card.getName(),
                card.getGrade(),
                card.getImageUrl(),
                auction.getStartingPrice(),
                auction.getHighestPrice(),
                auction.getBuyoutPrice(),
                auction.getStatus(),
                auction.getStartedAt(),
                auction.getEndedAt(),
                auction.getCreatedAt()
        );
    }

    private Map<Long, Long> toLongMap(List<AuctionCountProjection> projections) {
        return projections.stream()
                .collect(Collectors.toMap(AuctionCountProjection::getAuctionId, AuctionCountProjection::getCnt));
    }

    private record PopularAuctionItem(SearchAuctionResponse response, double popularityScore) {
    }
}
