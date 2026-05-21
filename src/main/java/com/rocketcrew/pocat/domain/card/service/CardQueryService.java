package com.rocketcrew.pocat.domain.card.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.card.dto.request.CardSearchCondition;
import com.rocketcrew.pocat.domain.card.dto.response.CardResponse;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.domain.order.dto.response.CardAveragePriceResponse;
import com.rocketcrew.pocat.domain.order.service.OrderQueryService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.CardException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CardQueryService {

    private static final long SEARCH_CACHE_TTL_MINUTES = 10;
    private static final long AVG_PRICE_CACHE_TTL_HOURS = 1;
    private static final String SEARCH_CACHE_PREFIX = "card:search:";
    private static final String AVG_PRICE_CACHE_PREFIX = "card:avgprice:";

    private final CardRepository cardRepository;
    private final OrderQueryService orderQueryService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Page<CardResponse> getCards(CardSearchCondition condition, Pageable pageable) {
        if (StringUtils.hasText(condition.keyword()) && condition.keyword().trim().length() < 2) {
            throw new CardException(ErrorCode.INVALID_INPUT);
        }

        String cacheKey = buildSearchCacheKey(condition, pageable);
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                CardSearchCacheDto dto = objectMapper.readValue(cached, CardSearchCacheDto.class);
                return new PageImpl<>(dto.content(), pageable, dto.totalElements());
            } catch (Exception e) {
                log.warn("[CardCache] 검색 캐시 역직렬화 실패, DB 조회로 폴백: {}", e.getMessage());
            }
        }

        Page<CardResponse> page = cardRepository.searchCards(condition, pageable)
                .map(CardResponse::from);

        try {
            String json = objectMapper.writeValueAsString(
                    new CardSearchCacheDto(page.getContent(), page.getTotalElements()));
            redisTemplate.opsForValue().set(cacheKey, json, SEARCH_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("[CardCache] 검색 캐시 저장 실패: {}", e.getMessage());
        }

        return page;
    }

    public CardResponse getCard(Long id) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new CardException(ErrorCode.CARD_NOT_FOUND));
        if (card.getStatus() != CardStatus.ACTIVE) {
            throw new CardException(ErrorCode.CARD_NOT_FOUND);
        }
        return CardResponse.from(card);
    }

    public CardAveragePriceResponse getAveragePrice(Long cardId) {
        String cacheKey = AVG_PRICE_CACHE_PREFIX + cardId;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, CardAveragePriceResponse.class);
            } catch (Exception e) {
                log.warn("[CardCache] 평균가 캐시 역직렬화 실패, DB 조회로 폴백: {}", e.getMessage());
            }
        }

        CardAveragePriceResponse response = orderQueryService.getAveragePriceByCard(cardId);

        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(response),
                    AVG_PRICE_CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("[CardCache] 평균가 캐시 저장 실패: {}", e.getMessage());
        }

        return response;
    }

    /**
     * 카드 검색 조건 + 페이지 정보를 조합해 Redis 캐시 키를 생성한다.
     */
    private String buildSearchCacheKey(CardSearchCondition condition, Pageable pageable) {
        return SEARCH_CACHE_PREFIX +
                Objects.toString(condition.keyword(), "") + ":" +
                Objects.toString(condition.series(), "") + ":" +
                Objects.toString(condition.setName(), "") + ":" +
                Objects.toString(condition.grade(), "") + ":" +
                Objects.toString(condition.category(), "") + ":" +
                Objects.toString(condition.status(), "") + ":" +
                pageable.getPageNumber() + ":" +
                pageable.getPageSize() + ":" +
                pageable.getSort();
    }

    /**
     * Page&lt;CardResponse&gt;를 Redis에 저장하기 위한 직렬화 전용 DTO.
     * content(목록) + totalElements(전체 수)만 저장하고 복원 시 PageImpl로 재구성한다.
     */
    private record CardSearchCacheDto(List<CardResponse> content, long totalElements) {}
    public Card validateRegistrableForAuction(Long cardId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new CardException(ErrorCode.CARD_NOT_FOUND));
        if (card.getStatus() != CardStatus.ACTIVE) {
            throw new CardException(ErrorCode.CARD_NOT_ACTIVE);
        }
        return card;
    }
    
    public Page<CardResponse> getMyRequests(Long userId, CardStatus status, Pageable pageable) {
        // status 유무에 따라 분기 — 두 map() 중 하나만 실행되므로 이중 순회 없음
        if (status != null) {
            return cardRepository.findByUserIdAndStatus(userId, status, pageable)
                    .map(CardResponse::from); // Page<Card> → Page<CardResponse> 단일 순회
        }
        return cardRepository.findByUserId(userId, pageable)
                .map(CardResponse::from); // Page<Card> → Page<CardResponse> 단일 순회
    }

    public Page<CardResponse> getRequests(CardStatus status, Pageable pageable) {
        // status 유무에 따라 분기 — 두 map() 중 하나만 실행되므로 이중 순회 없음
        if (status != null) {
            return cardRepository.findByStatus(status, pageable)
                    .map(CardResponse::from); // Page<Card> → Page<CardResponse> 단일 순회
        }
        return cardRepository.findAll(pageable)
                .map(CardResponse::from); // Page<Card> → Page<CardResponse> 단일 순회
    }
}
