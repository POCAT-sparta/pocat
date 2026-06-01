package com.rocketcrew.pocat.domain.card.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.card.document.CardDocument;
import com.rocketcrew.pocat.domain.card.dto.request.CardSearchCondition;
import com.rocketcrew.pocat.domain.card.dto.response.CardResponse;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.card.entity.enums.CardSource;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.domain.order.dto.response.CardAveragePriceResponse;
import com.rocketcrew.pocat.domain.order.service.OrderQueryService;
import com.rocketcrew.pocat.domain.series.service.SeriesQueryService;
import com.rocketcrew.pocat.domain.set.service.PokemonSetQueryService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.CardException;
import com.rocketcrew.pocat.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CardQueryService")
class CardQueryServiceTest {

    @InjectMocks
    CardQueryService service;

    @Mock
    CardRepository cardRepository;

    @Mock
    AuctionRepository auctionRepository;

    @Mock
    OrderQueryService orderQueryService;

    @Mock
    StringRedisTemplate redisTemplate;

    @Mock
    ObjectMapper objectMapper;

    @Mock
    ElasticsearchOperations elasticsearchOperations;

    @Mock
    SeriesQueryService seriesQueryService;

    @Mock
    PokemonSetQueryService pokemonSetQueryService;

    // ---------------------------------------------------------------
    // 헬퍼
    // ---------------------------------------------------------------

    private Card buildCard(Long id, CardStatus status) {
        Card card = Card.builder()
                .userId(2L)
                .tcgdexId("swsh5-58")
                .name("피카츄")
                .series(TestFixtures.aSeries())
                .pokemonSet(TestFixtures.aPokemonSet())
                .cardNumber("058")
                .rarity("Rare")
                .category(CardCategory.POKEMON)
                .grade(CardGrade.PSA_10)
                .imageUrl("https://example.com/pikachu.jpg")
                .source(CardSource.TCGDEX)
                .status(status)
                .build();
        ReflectionTestUtils.setField(card, "id", id);
        return card;
    }

    @SuppressWarnings("unchecked")
    private SearchHits<CardDocument> emptySearchHits() {
        SearchHits<CardDocument> hits = mock(SearchHits.class);
        given(hits.stream()).willReturn(java.util.stream.Stream.empty());
        given(hits.getTotalHits()).willReturn(0L);
        return hits;
    }

    @SuppressWarnings("unchecked")
    private SearchHits<CardDocument> singleSearchHit(CardDocument document) {
        SearchHit<CardDocument> hit = mock(SearchHit.class);
        given(hit.getContent()).willReturn(document);

        SearchHits<CardDocument> hits = mock(SearchHits.class);
        given(hits.stream()).willReturn(java.util.stream.Stream.of(hit));
        given(hits.getTotalHits()).willReturn(1L);
        return hits;
    }

    // ---------------------------------------------------------------
    // getCards
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("getCards")
    class GetCards {

        @Test
        @DisplayName("실패: 키워드 1글자 → INVALID_INPUT")
        void fail_keywordTooShort() {
            CardSearchCondition condition = new CardSearchCondition("피", null, null, null, null, null, null);
            Pageable pageable = PageRequest.of(0, 20);

            assertThatThrownBy(() -> service.getCards(condition, pageable))
                    .isInstanceOf(CardException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
        }

        @Test
        @DisplayName("성공: 키워드 없이 조회 → ES 검색 결과 반환")
        void success_noKeyword() {
            CardSearchCondition condition = new CardSearchCondition(null, null, null, null, null, null, null);
            Pageable pageable = PageRequest.of(0, 20);

            SearchHits<CardDocument> hits = emptySearchHits();
            given(elasticsearchOperations.search(any(NativeQuery.class), eq(CardDocument.class)))
                    .willReturn(hits);

            Page<CardResponse> result = service.getCards(condition, pageable);

            assertThat(result).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("성공: 키워드 2글자 이상 → ES 검색 실행")
        void success_withKeyword() {
            CardSearchCondition condition = new CardSearchCondition("피카츄", null, null, null, null, null, null);
            Pageable pageable = PageRequest.of(0, 20);

            SearchHits<CardDocument> hits = emptySearchHits();
            given(elasticsearchOperations.search(any(NativeQuery.class), eq(CardDocument.class)))
                    .willReturn(hits);

            Page<CardResponse> result = service.getCards(condition, pageable);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("성공: 결과 있을 때 경매 수 배치 조회 포함")
        void success_withAuctionCount() {
            CardDocument document = CardDocument.builder()
                    .id("3")
                    .userId(2L)
                    .name("피카츄")
                    .series("Sword & Shield")
                    .status("ACTIVE")
                    .grade("PSA_10")
                    .category("POKEMON")
                    .source("TCGDEX")
                    .rarity("Rare")
                    .cardNumber("058")
                    .build();

            CardSearchCondition condition = new CardSearchCondition(null, null, null, null, null, null, null);
            Pageable pageable = PageRequest.of(0, 20);

            SearchHits<CardDocument> hits = singleSearchHit(document);
            given(elasticsearchOperations.search(any(NativeQuery.class), eq(CardDocument.class)))
                    .willReturn(hits);

            // 경매 수 배치 조회 결과 설정 (DB GROUP BY 쿼리)
            AuctionRepository.CardAuctionCountView countView = mock(AuctionRepository.CardAuctionCountView.class);
            given(countView.getCardId()).willReturn(3L);
            given(countView.getAuctionCount()).willReturn(2L);
            given(auctionRepository.countGroupByCardId(List.of(3L), AuctionStatus.ACTIVE))
                    .willReturn(List.of(countView));

            Page<CardResponse> result = service.getCards(condition, pageable);

            assertThat(result).hasSize(1);
            assertThat(result.getContent().get(0).activeAuctionCount()).isEqualTo(2);
        }
    }

    // ---------------------------------------------------------------
    // getCard
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("getCard")
    class GetCard {

        @Test
        @DisplayName("성공: ACTIVE 카드 단건 조회")
        void success() {
            Card card = buildCard(3L, CardStatus.ACTIVE);
            given(cardRepository.findById(3L)).willReturn(Optional.of(card));
            given(auctionRepository.countByCardIdAndStatus(3L, AuctionStatus.ACTIVE)).willReturn(1L);

            CardResponse response = service.getCard(3L);

            assertThat(response.id()).isEqualTo(3L);
            assertThat(response.activeAuctionCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("실패: INACTIVE 카드 → CARD_NOT_FOUND")
        void fail_inactiveCard() {
            Card card = buildCard(3L, CardStatus.REJECTED);
            given(cardRepository.findById(3L)).willReturn(Optional.of(card));

            assertThatThrownBy(() -> service.getCard(3L))
                    .isInstanceOf(CardException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CARD_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 카드 없음 → CARD_NOT_FOUND")
        void fail_notFound() {
            given(cardRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.getCard(99L))
                    .isInstanceOf(CardException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CARD_NOT_FOUND);
        }
    }

    // ---------------------------------------------------------------
    // getCardAuctions
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("getCardAuctions")
    class GetCardAuctions {

        @Test
        @DisplayName("성공: ACTIVE 카드의 경매 목록 조회")
        void success() {
            Card card = buildCard(3L, CardStatus.ACTIVE);
            Pageable pageable = PageRequest.of(0, 10);
            given(cardRepository.findById(3L)).willReturn(Optional.of(card));
            given(auctionRepository.findByCardIdAndStatusOrderByStartedAtDescIdDesc(3L, AuctionStatus.ACTIVE, pageable))
                    .willReturn(new PageImpl<>(Collections.emptyList()));

            Page<?> result = service.getCardAuctions(3L, pageable);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("실패: 카드 없음 → CARD_NOT_FOUND")
        void fail_notFound() {
            given(cardRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.getCardAuctions(99L, PageRequest.of(0, 10)))
                    .isInstanceOf(CardException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CARD_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: INACTIVE 카드 → CARD_NOT_FOUND")
        void fail_inactiveCard() {
            Card card = buildCard(3L, CardStatus.PENDING);
            given(cardRepository.findById(3L)).willReturn(Optional.of(card));

            assertThatThrownBy(() -> service.getCardAuctions(3L, PageRequest.of(0, 10)))
                    .isInstanceOf(CardException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CARD_NOT_FOUND);
        }
    }

    // ---------------------------------------------------------------
    // getCardEntity
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("getCardEntity")
    class GetCardEntity {

        @Test
        @DisplayName("성공: 카드 엔티티 반환")
        void success() {
            Card card = buildCard(3L, CardStatus.ACTIVE);
            given(cardRepository.findById(3L)).willReturn(Optional.of(card));

            Card result = service.getCardEntity(3L);

            assertThat(result.getId()).isEqualTo(3L);
        }

        @Test
        @DisplayName("실패: 카드 없음 → CARD_NOT_FOUND")
        void fail_notFound() {
            given(cardRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.getCardEntity(99L))
                    .isInstanceOf(CardException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CARD_NOT_FOUND);
        }
    }

    // ---------------------------------------------------------------
    // getCardEntities
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("getCardEntities")
    class GetCardEntities {

        @Test
        @DisplayName("성공: 여러 카드 ID 일괄 조회")
        void success() {
            Card card = buildCard(3L, CardStatus.ACTIVE);
            given(cardRepository.findAllById(List.of(3L))).willReturn(List.of(card));

            Map<Long, Card> result = service.getCardEntities(List.of(3L));

            assertThat(result).containsKey(3L);
        }

        @Test
        @DisplayName("성공: 빈 ID 목록 → 빈 맵 반환")
        void success_emptyList() {
            Map<Long, Card> result = service.getCardEntities(Collections.emptyList());

            assertThat(result).isEmpty();
            verify(cardRepository, never()).findAllById(any());
        }

        @Test
        @DisplayName("실패: 일부 ID 누락 → CARD_NOT_FOUND")
        void fail_missingIds() {
            Card card = buildCard(3L, CardStatus.ACTIVE);
            given(cardRepository.findAllById(List.of(3L, 99L))).willReturn(List.of(card));

            assertThatThrownBy(() -> service.getCardEntities(List.of(3L, 99L)))
                    .isInstanceOf(CardException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CARD_NOT_FOUND);
        }
    }

    // ---------------------------------------------------------------
    // getAveragePrice
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("getAveragePrice")
    class GetAveragePrice {

        @Test
        @DisplayName("성공: Redis 캐시 히트 → DB 조회 없음")
        void success_cacheHit() throws Exception {
            String cached = "{\"cardId\":3,\"averagePrice\":5000,\"transactionCount\":3}";
            CardAveragePriceResponse cachedResponse = new CardAveragePriceResponse(3L, 5000L, 3L, null, null);

            @SuppressWarnings("unchecked")
            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            given(redisTemplate.opsForValue()).willReturn(valueOps);
            given(valueOps.get("card:avgprice:3")).willReturn(cached);
            given(objectMapper.readValue(cached, CardAveragePriceResponse.class)).willReturn(cachedResponse);

            CardAveragePriceResponse result = service.getAveragePrice(3L);

            assertThat(result.averagePrice()).isEqualTo(5000L);
            verify(orderQueryService, never()).getAveragePriceByCard(anyLong());
        }

        @Test
        @DisplayName("성공: Redis 캐시 미스 → DB 조회 후 캐시 저장")
        void success_cacheMiss() throws Exception {
            CardAveragePriceResponse dbResponse = new CardAveragePriceResponse(3L, 7000L, 5L, null, null);

            @SuppressWarnings("unchecked")
            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            given(redisTemplate.opsForValue()).willReturn(valueOps);
            given(valueOps.get("card:avgprice:3")).willReturn(null);
            given(orderQueryService.getAveragePriceByCard(3L)).willReturn(dbResponse);
            given(objectMapper.writeValueAsString(dbResponse)).willReturn("{\"cardId\":3,\"averagePrice\":7000,\"count\":5}");

            CardAveragePriceResponse result = service.getAveragePrice(3L);

            assertThat(result.averagePrice()).isEqualTo(7000L);
            verify(orderQueryService).getAveragePriceByCard(3L);
            verify(valueOps).set(eq("card:avgprice:3"), anyString(), eq(1L), eq(TimeUnit.HOURS));
        }
    }

    // ---------------------------------------------------------------
    // validateRegistrableForAuction
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("validateRegistrableForAuction")
    class ValidateRegistrableForAuction {

        @Test
        @DisplayName("성공: ACTIVE 카드 → 카드 엔티티 반환")
        void success() {
            Card card = buildCard(3L, CardStatus.ACTIVE);
            given(cardRepository.findById(3L)).willReturn(Optional.of(card));

            Card result = service.validateRegistrableForAuction(3L);

            assertThat(result.getStatus()).isEqualTo(CardStatus.ACTIVE);
        }

        @Test
        @DisplayName("실패: PENDING 카드 → CARD_NOT_ACTIVE")
        void fail_notActive() {
            Card card = buildCard(3L, CardStatus.PENDING);
            given(cardRepository.findById(3L)).willReturn(Optional.of(card));

            assertThatThrownBy(() -> service.validateRegistrableForAuction(3L))
                    .isInstanceOf(CardException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CARD_NOT_ACTIVE);
        }

        @Test
        @DisplayName("실패: 카드 없음 → CARD_NOT_FOUND")
        void fail_notFound() {
            given(cardRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.validateRegistrableForAuction(99L))
                    .isInstanceOf(CardException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CARD_NOT_FOUND);
        }
    }
}
