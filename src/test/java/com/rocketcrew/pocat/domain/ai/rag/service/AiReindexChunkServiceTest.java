package com.rocketcrew.pocat.domain.ai.rag.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import com.rocketcrew.pocat.domain.ai.rag.dto.ReindexChunkResponse;
import com.rocketcrew.pocat.domain.ai.rag.exception.EmbeddingRateLimitedException;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * AiReindexChunkService 단위 테스트 (RED).
 *
 * <p>pocat-batch에서 호출되는 카드 임베딩 청크 재색인 로직을 검증한다.
 * <ol>
 *   <li>ES(pocat-ai-index)에서 metadata.cardId.keyword terms query로 기인덱싱 cardId 조회</li>
 *   <li>기인덱싱된 cardId는 skip</li>
 *   <li>미인덱싱 cardId만 embeddingService.embedCardRateLimited() 호출</li>
 *   <li>rate limit 도달(EmbeddingRateLimitedException) 시 잔여 처리 중단 + rateLimited=true</li>
 * </ol>
 */
@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
@DisplayName("AiReindexChunkService")
class AiReindexChunkServiceTest {

    @InjectMocks
    private AiReindexChunkService aiReindexChunkService;

    @Mock
    private ElasticsearchClient esClient;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private SearchResponse<Map> searchResponse;

    @Mock
    private HitsMetadata<Map> hitsMetadata;

    /**
     * ES 검색 결과로 이미 인덱싱된 cardId 목록을 반환하도록 mock 구성.
     */
    private void givenAlreadyIndexedCardIds(List<Long> indexedCardIds) throws IOException {
        List<Hit<Map>> hits = indexedCardIds.stream()
                .map(cardId -> {
                    Hit<Map> hit = org.mockito.Mockito.mock(Hit.class);
                    given(hit.source()).willReturn(Map.of("metadata", Map.of("cardId", String.valueOf(cardId))));
                    return hit;
                })
                .toList();

        given(hitsMetadata.hits()).willReturn(hits);
        given(searchResponse.hits()).willReturn(hitsMetadata);
        given(esClient.search(any(Function.class), eq(Map.class))).willReturn(searchResponse);
    }

    private Card cardWithId(Long id) {
        Card card = org.mockito.Mockito.mock(Card.class);
        given(card.getId()).willReturn(id);
        given(card.getName()).willReturn("card-" + id);
        return card;
    }

    @Nested
    @DisplayName("기인덱싱 카드 필터링")
    class SkipAlreadyIndexed {

        @Test
        @DisplayName("ES에 이미 인덱싱된 cardId는 skip하고 미인덱싱 cardId만 임베딩한다")
        void skipsAlreadyIndexedCards() throws IOException {
            // given: cardId 1,2,3 요청 중 1은 이미 인덱싱됨
            givenAlreadyIndexedCardIds(List.of(1L));
            Card card2 = cardWithId(2L);
            Card card3 = cardWithId(3L);
            given(cardRepository.findAllById(any())).willReturn(List.of(card2, card3));

            // when
            ReindexChunkResponse response = aiReindexChunkService.reindex(List.of(1L, 2L, 3L));

            // then
            verify(embeddingService, never()).embedCardRateLimited(eq(1L), org.mockito.ArgumentMatchers.anyString());
            verify(embeddingService).embedCardRateLimited(eq(2L), org.mockito.ArgumentMatchers.anyString());
            verify(embeddingService).embedCardRateLimited(eq(3L), org.mockito.ArgumentMatchers.anyString());

            assertThat(response.processedCount()).isEqualTo(3);
            assertThat(response.skippedCount()).isEqualTo(1);
            assertThat(response.indexedCount()).isEqualTo(2);
            assertThat(response.failedCount()).isEqualTo(0);
            assertThat(response.rateLimited()).isFalse();
        }

        @Test
        @DisplayName("모든 cardId가 이미 인덱싱된 경우 임베딩을 호출하지 않는다")
        void allAlreadyIndexed_doesNotCallEmbedding() throws IOException {
            // given
            givenAlreadyIndexedCardIds(List.of(10L, 20L));

            // when
            ReindexChunkResponse response = aiReindexChunkService.reindex(List.of(10L, 20L));

            // then
            verify(embeddingService, never()).embedCardRateLimited(anyLong(), org.mockito.ArgumentMatchers.anyString());

            assertThat(response.processedCount()).isEqualTo(2);
            assertThat(response.skippedCount()).isEqualTo(2);
            assertThat(response.indexedCount()).isEqualTo(0);
            assertThat(response.failedCount()).isEqualTo(0);
            assertThat(response.rateLimited()).isFalse();
        }
    }

    @Nested
    @DisplayName("통계 집계")
    class StatAggregation {

        @Test
        @DisplayName("임베딩 중 일반 예외가 발생하면 failedCount를 증가시키고 계속 진행한다")
        void embeddingFailure_incrementsFailedCountAndContinues() throws IOException {
            // given
            givenAlreadyIndexedCardIds(List.of());
            Card card1 = cardWithId(1L);
            Card card2 = cardWithId(2L);
            given(cardRepository.findAllById(any())).willReturn(List.of(card1, card2));

            willThrow(new RuntimeException("embedding failed"))
                    .given(embeddingService).embedCardRateLimited(eq(1L), org.mockito.ArgumentMatchers.anyString());

            // when
            ReindexChunkResponse response = aiReindexChunkService.reindex(List.of(1L, 2L));

            // then: cardId=1은 실패, cardId=2는 정상 처리되어야 함
            verify(embeddingService).embedCardRateLimited(eq(2L), org.mockito.ArgumentMatchers.anyString());

            assertThat(response.processedCount()).isEqualTo(2);
            assertThat(response.failedCount()).isEqualTo(1);
            assertThat(response.indexedCount()).isEqualTo(1);
            assertThat(response.rateLimited()).isFalse();
        }
    }

    @Nested
    @DisplayName("rate limit 도달")
    class RateLimitReached {

        @Test
        @DisplayName("EmbeddingRateLimitedException 발생 시 잔여 카드 처리를 중단하고 rateLimited=true를 반환한다")
        void rateLimitException_stopsProcessingRemainingCards() throws IOException {
            // given: cardId 1,2,3 모두 미인덱싱, cardId=1에서 rate limit 도달
            givenAlreadyIndexedCardIds(List.of());
            Card card1 = cardWithId(1L);
            given(cardRepository.findAllById(any())).willReturn(List.of(card1));

            willThrow(new EmbeddingRateLimitedException("rate limit exceeded"))
                    .given(embeddingService).embedCardRateLimited(eq(1L), org.mockito.ArgumentMatchers.anyString());

            // when
            ReindexChunkResponse response = aiReindexChunkService.reindex(List.of(1L, 2L, 3L));

            // then: cardId 2, 3은 처리되지 않아야 함 (조기 종료)
            verify(embeddingService, never()).embedCardRateLimited(eq(2L), org.mockito.ArgumentMatchers.anyString());
            verify(embeddingService, never()).embedCardRateLimited(eq(3L), org.mockito.ArgumentMatchers.anyString());

            assertThat(response.rateLimited()).isTrue();
            assertThat(response.indexedCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("findAlreadyIndexedCardIds")
    class FindAlreadyIndexedCardIds {

        @Test
        @DisplayName("cardIds가 비어있으면 ES 조회 없이 빈 결과로 처리한다")
        void emptyCardIds_returnsEmptyResponseWithoutEsQuery() throws IOException {
            // when
            ReindexChunkResponse response = aiReindexChunkService.reindex(List.of());

            // then: ES 조회가 발생하지 않아야 함
            verify(embeddingService, never()).embedCardRateLimited(anyLong(), org.mockito.ArgumentMatchers.anyString());
            verify(esClient, never()).search(any(Function.class), eq(Map.class));

            assertThat(response.processedCount()).isEqualTo(0);
            assertThat(response.skippedCount()).isEqualTo(0);
            assertThat(response.indexedCount()).isEqualTo(0);
            assertThat(response.failedCount()).isEqualTo(0);
            assertThat(response.rateLimited()).isFalse();
        }

        @Test
        @DisplayName("ES 조회 중 IOException 발생 시 전체 cardId를 미인덱싱으로 간주하고(fail-open) 임베딩을 진행한다")
        void esQueryThrowsIOException_treatsAllCardIdsAsUnindexed() throws IOException {
            // given
            given(esClient.search(any(Function.class), eq(Map.class))).willThrow(new IOException("ES connection failed"));
            Card card1 = cardWithId(1L);
            Card card2 = cardWithId(2L);
            given(cardRepository.findAllById(any())).willReturn(List.of(card1, card2));

            // when
            ReindexChunkResponse response = aiReindexChunkService.reindex(List.of(1L, 2L));

            // then: ES 조회 실패 시 모든 cardId를 미인덱싱으로 처리하여 임베딩 시도
            verify(embeddingService).embedCardRateLimited(eq(1L), org.mockito.ArgumentMatchers.anyString());
            verify(embeddingService).embedCardRateLimited(eq(2L), org.mockito.ArgumentMatchers.anyString());

            assertThat(response.processedCount()).isEqualTo(2);
            assertThat(response.skippedCount()).isEqualTo(0);
            assertThat(response.indexedCount()).isEqualTo(2);
            assertThat(response.failedCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("ES terms query 시 cardIds 개수만큼 size를 지정하여 모든 매칭 결과를 조회한다")
        void findAlreadyIndexedCardIds_setsSizeToCardIdsCount() throws IOException {
            // given
            givenAlreadyIndexedCardIds(List.of());
            List<Long> cardIds = java.util.stream.LongStream.rangeClosed(1, 50).boxed().toList();

            // when
            aiReindexChunkService.reindex(cardIds);

            // then
            ArgumentCaptor<Function<co.elastic.clients.elasticsearch.core.SearchRequest.Builder,
                    co.elastic.clients.util.ObjectBuilder<co.elastic.clients.elasticsearch.core.SearchRequest>>> captor =
                    ArgumentCaptor.forClass(Function.class);
            verify(esClient).search(captor.capture(), eq(Map.class));

            co.elastic.clients.elasticsearch.core.SearchRequest request =
                    captor.getValue().apply(new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()).build();
            assertThat(request.size()).isEqualTo(50);
        }
    }
}
