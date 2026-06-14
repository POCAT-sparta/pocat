package com.rocketcrew.pocat.domain.ai.rag.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.rocketcrew.pocat.domain.ai.rag.dto.ReindexChunkResponse;
import com.rocketcrew.pocat.domain.ai.rag.exception.EmbeddingRateLimitedException;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 카드 임베딩 청크 재색인 서비스.
 *
 * <p>pocat-batch에서 호출되는 internal API의 핵심 로직.
 * <ol>
 *   <li>ES(pocat-ai-index)에서 metadata.cardId.keyword terms query로 기인덱싱 cardId 조회</li>
 *   <li>기인덱싱된 cardId는 skip</li>
 *   <li>미인덱싱 cardId만 embeddingService.embedCardRateLimited() 호출</li>
 *   <li>rate limit 도달(EmbeddingRateLimitedException) 시 잔여 처리 중단 + rateLimited=true</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiReindexChunkService {

    private static final String AI_INDEX = "pocat-ai-index";

    private final ElasticsearchClient esClient;
    private final CardRepository cardRepository;
    private final EmbeddingService embeddingService;

    /**
     * 요청받은 카드 ID 목록 중 ES에 기인덱싱되지 않은 카드만 임베딩한다.
     *
     * @param cardIds 재색인 대상 카드 ID 목록
     * @return 처리 통계
     */
    public ReindexChunkResponse reindex(List<Long> cardIds) {
        int processedCount = cardIds.size();
        Set<Long> alreadyIndexedCardIds = findAlreadyIndexedCardIds(cardIds);

        int skippedCount = 0;
        int indexedCount = 0;
        int failedCount = 0;
        boolean rateLimited = false;

        List<Long> targetCardIds = cardIds.stream()
                .filter(cardId -> !alreadyIndexedCardIds.contains(cardId))
                .toList();
        Map<Long, Card> cardsById = cardRepository.findAllById(targetCardIds).stream()
                .collect(Collectors.toMap(Card::getId, card -> card));

        for (Long cardId : cardIds) {
            if (alreadyIndexedCardIds.contains(cardId)) {
                skippedCount++;
                continue;
            }

            try {
                Card card = cardsById.get(cardId);
                if (card == null) {
                    log.warn("[AI_REINDEX_CHUNK] 카드를 찾을 수 없음, 건너뜀: cardId={}", cardId);
                    failedCount++;
                    continue;
                }

                String cardText = buildCardText(card);
                embeddingService.embedCardRateLimited(cardId, cardText);
                indexedCount++;
            } catch (EmbeddingRateLimitedException e) {
                log.warn("[AI_REINDEX_CHUNK] rate limit 도달, 잔여 카드 처리 중단: cardId={}", cardId);
                rateLimited = true;
                break;
            } catch (Exception e) {
                log.error("[AI_REINDEX_CHUNK] 카드 임베딩 실패: cardId={}", cardId, e);
                failedCount++;
            }
        }

        return new ReindexChunkResponse(processedCount, skippedCount, indexedCount, failedCount, rateLimited);
    }

    /**
     * ES(pocat-ai-index)에서 metadata.cardId.keyword terms query로 기인덱싱된 cardId를 조회한다.
     */
    private Set<Long> findAlreadyIndexedCardIds(List<Long> cardIds) {
        if (cardIds.isEmpty()) {
            return Set.of();
        }

        try {
            List<FieldValue> values = cardIds.stream()
                    .map(id -> FieldValue.of(String.valueOf(id)))
                    .toList();

            SearchResponse<Map> response = esClient.search(s -> s
                            .index(AI_INDEX)
                            .size(cardIds.size())
                            .query(q -> q.terms(TermsQuery.of(t -> t
                                    .field("metadata.cardId.keyword")
                                    .terms(TermsQueryField.of(tf -> tf.value(values)))
                            ))),
                    Map.class);

            Set<Long> indexedCardIds = new HashSet<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map<?, ?> source = hit.source();
                if (source == null) {
                    continue;
                }
                Object metadataObj = source.get("metadata");
                if (metadataObj instanceof Map<?, ?> metadata) {
                    Object cardIdObj = metadata.get("cardId");
                    if (cardIdObj != null) {
                        try {
                            indexedCardIds.add(Long.parseLong(String.valueOf(cardIdObj)));
                        } catch (NumberFormatException nfe) {
                            log.warn("[AI_REINDEX_CHUNK] ES 문서의 cardId 형식이 올바르지 않아 건너뜀: value={}", cardIdObj);
                        }
                    }
                }
            }
            return indexedCardIds;
        } catch (IOException | RuntimeException e) {
            log.error("[AI_REINDEX_CHUNK] ES 기인덱싱 카드 조회 실패, 전체 카드를 미인덱싱으로 처리합니다.", e);
            return Set.of();
        }
    }

    private String buildCardText(Card card) {
        return String.format(
                "카드 이름: %s\n등급: %s\n시리즈: %s\n세트: %s\nURL: %s\n레어도: %s",
                card.getName(),
                card.getGrade() != null ? card.getGrade().toString() : "N/A",
                card.getSeries() != null ? card.getSeries().getName() : "N/A",
                card.getPokemonSet() != null ? card.getPokemonSet().getName() : "N/A",
                card.getImageUrl() != null ? card.getImageUrl() : "N/A",
                card.getRarity()
        );
    }
}
