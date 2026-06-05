package com.rocketcrew.pocat.domain.card.service;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.card.document.CardDocument;
import com.rocketcrew.pocat.domain.card.dto.request.CardSearchCondition;
import com.rocketcrew.pocat.domain.card.dto.response.ActiveAuctionSummary;
import com.rocketcrew.pocat.domain.card.dto.response.CardResponse;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.domain.order.dto.response.CardAveragePriceResponse;
import com.rocketcrew.pocat.domain.order.service.OrderQueryService;
import com.rocketcrew.pocat.domain.series.service.SeriesQueryService;
import com.rocketcrew.pocat.domain.set.service.PokemonSetQueryService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.CardException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CardQueryService {

    private static final long AVG_PRICE_CACHE_TTL_HOURS = 1;
    private static final String AVG_PRICE_CACHE_PREFIX = "card:avgprice:";

    private final CardRepository cardRepository;
    private final AuctionRepository auctionRepository;
    private final OrderQueryService orderQueryService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ElasticsearchOperations elasticsearchOperations;
    private final SeriesQueryService seriesQueryService;
    private final PokemonSetQueryService pokemonSetQueryService;

    public Page<CardResponse> getCards(CardSearchCondition condition, Pageable pageable) {
        if (StringUtils.hasText(condition.keyword()) && condition.keyword().trim().length() < 2) {
            throw new CardException(ErrorCode.INVALID_INPUT);
        }

        BoolQuery.Builder bool = new BoolQuery.Builder()
                .filter(TermQuery.of(t -> t.field("status").value("ACTIVE"))._toQuery());

        if (StringUtils.hasText(condition.keyword())) {
            // cross_fields + AND: 여러 단어를 입력하면 모든 토큰이 필드 전체에 걸쳐 존재해야 매칭
            // 예) "반역크래시 리자몽" → setNameKo에 "반역크래시" AND nameKo에 "리자몽" → 교집합
            bool.must(MultiMatchQuery.of(m -> m
                    .fields("name", "nameKo", "series.text", "seriesKo", "setName.text", "setNameKo")
                    .query(condition.keyword())
                    .type(TextQueryType.CrossFields)
                    .operator(Operator.And))._toQuery());
        }
        if (StringUtils.hasText(condition.series())) {
            String seriesEn = seriesQueryService.translate(condition.series());
            bool.filter(TermQuery.of(t -> t.field("series").value(seriesEn))._toQuery());
        }
        if (StringUtils.hasText(condition.setName())) {
            String setNameEn = pokemonSetQueryService.translate(condition.setName());
            bool.filter(TermQuery.of(t -> t.field("setName").value(setNameEn))._toQuery());
        }
        if (condition.grade() != null) {
            bool.filter(TermQuery.of(t -> t.field("grade").value(condition.grade().name()))._toQuery());
        }
        if (StringUtils.hasText(condition.rarity())) {
            bool.filter(TermQuery.of(t -> t.field("rarity").value(condition.rarity()))._toQuery());
        }
        if (condition.category() != null) {
            bool.filter(TermQuery.of(t -> t.field("category").value(condition.category().name()))._toQuery());
        }

        // 키워드 검색 시 관련도(_score) 기준 정렬, 그 외엔 pageable 정렬(기본: createdAt DESC) 사용
        NativeQueryBuilder queryBuilder = NativeQuery.builder()
                .withQuery(bool.build()._toQuery());

        if (StringUtils.hasText(condition.keyword())) {
            queryBuilder
                    .withPageable(PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()))
                    .withSort(List.of(SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc)))));
        } else {
            queryBuilder.withPageable(pageable);
        }

        NativeQuery query = queryBuilder.build();

        SearchHits<CardDocument> hits = elasticsearchOperations.search(query, CardDocument.class);
        List<CardDocument> documents = hits.stream()
                .map(SearchHit::getContent)
                .toList();

        // 진행 중인 경매(ACTIVE) 건수를 카드 ID 단위로 배치 집계 → 단일 쿼리
        Map<Long, Long> auctionCountMap = Collections.emptyMap();
        if (!documents.isEmpty()) {
            List<Long> cardIds = documents.stream()
                    .map(doc -> Long.parseLong(doc.getId()))
                    .toList();
            auctionCountMap = auctionRepository.countGroupByCardId(cardIds, AuctionStatus.ACTIVE)
                    .stream()
                    .collect(Collectors.toMap(
                            AuctionRepository.CardAuctionCountView::getCardId,
                            AuctionRepository.CardAuctionCountView::getAuctionCount
                    ));
        }

        final Map<Long, Long> countMap = auctionCountMap;
        List<CardResponse> content = documents.stream()
                .map(doc -> doc.toResponse()
                        .withActiveAuctionCount(
                                countMap.getOrDefault(Long.parseLong(doc.getId()), 0L).intValue()))
                .toList();

        return new PageImpl<>(content, pageable, hits.getTotalHits());
    }

    public CardResponse getCard(Long id) {
        Card card = getCardEntity(id);
        if (card.getStatus() != CardStatus.ACTIVE) {
            throw new CardException(ErrorCode.CARD_NOT_FOUND);
        }
        int count = (int) auctionRepository.countByCardIdAndStatus(id, AuctionStatus.ACTIVE);
        return CardResponse.from(card).withActiveAuctionCount(count);
    }

    public Page<ActiveAuctionSummary> getCardAuctions(Long cardId, Pageable pageable) {
        Card card = getCardEntity(cardId);
        if (card.getStatus() != CardStatus.ACTIVE) {
            throw new CardException(ErrorCode.CARD_NOT_FOUND);
        }
        return auctionRepository
                .findByCardIdAndStatusOrderByStartedAtDescIdDesc(cardId, AuctionStatus.ACTIVE, pageable)
                .map(ActiveAuctionSummary::from);
    }

    public Card getCardEntity(Long id) {
        return cardRepository.findById(id)
                .orElseThrow(() -> new CardException(ErrorCode.CARD_NOT_FOUND));
    }

    public Map<Long, Card> getCardEntities(List<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> distinctIds = ids.stream().distinct().toList();
        Map<Long, Card> cardMap = cardRepository.findAllById(distinctIds).stream()
                .collect(Collectors.toMap(Card::getId, Function.identity()));

        List<Long> missingIds = distinctIds.stream()
                .filter(id -> !cardMap.containsKey(id))
                .toList();

        if (!missingIds.isEmpty()) {
            log.warn("Card entities missing for requested ids. missingIds={}", missingIds);
            throw new CardException(ErrorCode.CARD_NOT_FOUND);
        }

        return cardMap;
    }

    public CardAveragePriceResponse getAveragePrice(Long cardId) {
        String cacheKey = AVG_PRICE_CACHE_PREFIX + cardId;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.readValue(cached, CardAveragePriceResponse.class);
            }
        } catch (Exception e) {
            log.warn("[CACHE] 카드 평균가 캐시 조회/역직렬화 실패, DB 조회로 폴백 cardId={}: {}", cardId, e.getMessage());
        }

        CardAveragePriceResponse response = orderQueryService.getAveragePriceByCard(cardId);

        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(response),
                    AVG_PRICE_CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("[CACHE] 카드 평균가 캐시 저장 실패 cardId={}: {}", cardId, e.getMessage());
        }

        return response;
    }

    public Card validateRegistrableForAuction(Long cardId) {
        Card card = getCardEntity(cardId);
        if (card.getStatus() != CardStatus.ACTIVE) {
            throw new CardException(ErrorCode.CARD_NOT_ACTIVE);
        }
        return card;
    }

    public Page<CardResponse> getMyRequests(Long userId, CardStatus status, Pageable pageable) {
        if (status != null) {
            return cardRepository.findByUserIdAndStatus(userId, status, pageable)
                    .map(CardResponse::from);
        }
        return cardRepository.findByUserId(userId, pageable)
                .map(CardResponse::from);
    }

    public Page<CardResponse> getRequests(CardStatus status, Pageable pageable) {
        if (status != null) {
            return cardRepository.findByStatus(status, pageable)
                    .map(CardResponse::from);
        }
        return cardRepository.findAll(pageable)
                .map(CardResponse::from);
    }
}
