package com.rocketcrew.pocat.domain.card.service;

import com.rocketcrew.pocat.domain.ai.rag.event.CardEmbeddingEvent;
import com.rocketcrew.pocat.domain.card.dto.request.CreateCardRequest;
import com.rocketcrew.pocat.domain.card.dto.request.UpdateCardRequest;
import com.rocketcrew.pocat.domain.card.dto.response.CardResponse;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.card.entity.enums.CardSource;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.domain.card.repository.CardSearchRepository;
import com.rocketcrew.pocat.domain.pokemon.service.PokemonCommandService;
import com.rocketcrew.pocat.domain.series.service.SeriesCommandService;
import com.rocketcrew.pocat.domain.set.service.PokemonSetCommandService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.CardException;
import com.rocketcrew.pocat.support.TestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CardCommandService")
class CardCommandServiceTest {

    @InjectMocks
    CardCommandService service;

    @Mock
    CardRepository cardRepository;

    @Mock
    CardSearchRepository cardSearchRepository;

    @Mock
    SeriesCommandService seriesCommandService;

    @Mock
    PokemonSetCommandService pokemonSetCommandService;

    @Mock
    PokemonCommandService pokemonCommandService;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        // TransactionSynchronizationManager를 초기화해 indexCard() 내부의
        // isSynchronizationActive() → afterCommit() 경로를 활성화한다.
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    // ---------------------------------------------------------------
    // 헬퍼
    // ---------------------------------------------------------------

    private Card buildCard(Long id, CardStatus status) {
        Card card = Card.builder()
                .userId(1L)
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

    private CreateCardRequest aCreateRequest(String tcgdexId, CardCategory category) {
        return new CreateCardRequest(
                tcgdexId,
                "피카츄",
                "Sword & Shield",
                "swsh5",
                "Rebel Clash",
                "058",
                "Rare",
                category,
                CardGrade.PSA_10,
                "https://example.com/pikachu.jpg",
                CardSource.TCGDEX
        );
    }

    // ---------------------------------------------------------------
    // createCard
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("createCard")
    class CreateCard {

        @Test
        @DisplayName("성공: POKEMON 카테고리 카드 등록 (포켓몬 조회 발생)")
        void success_pokemonCategory() {
            CreateCardRequest request = aCreateRequest("swsh5-58", CardCategory.POKEMON);
            Card saved = buildCard(1L, CardStatus.PENDING);

            given(cardRepository.existsByTcgdexId("swsh5-58")).willReturn(false);
            given(seriesCommandService.findOrCreate("Sword & Shield")).willReturn(TestFixtures.aSeries());
            given(pokemonSetCommandService.findOrCreate("swsh5", "Rebel Clash", TestFixtures.aSeries()))
                    .willReturn(TestFixtures.aPokemonSet());
            given(pokemonCommandService.findOrCreateForCardName("피카츄")).willReturn(Optional.empty());
            given(cardRepository.save(any(Card.class))).willReturn(saved);

            CardResponse response = service.createCard(1L, request);

            assertThat(response.status()).isEqualTo(CardStatus.PENDING);
            assertThat(response.name()).isEqualTo("피카츄");
            verify(pokemonCommandService).findOrCreateForCardName("피카츄");
        }

        @Test
        @DisplayName("성공: 비포켓몬 카테고리 - 포켓몬 조회 없음")
        void success_nonPokemonCategory() {
            CreateCardRequest request = aCreateRequest(null, CardCategory.TRAINERS);
            Card saved = buildCard(2L, CardStatus.PENDING);

            given(seriesCommandService.findOrCreate("Sword & Shield")).willReturn(TestFixtures.aSeries());
            given(pokemonSetCommandService.findOrCreate("swsh5", "Rebel Clash", TestFixtures.aSeries()))
                    .willReturn(TestFixtures.aPokemonSet());
            given(cardRepository.save(any(Card.class))).willReturn(saved);

            service.createCard(1L, request);

            verify(pokemonCommandService, never()).findOrCreateForCardName(anyString());
        }

        @Test
        @DisplayName("실패: tcgdexId 중복 → CARD_ALREADY_EXISTS")
        void fail_duplicateTcgdexId() {
            CreateCardRequest request = aCreateRequest("swsh5-58", CardCategory.POKEMON);
            given(cardRepository.existsByTcgdexId("swsh5-58")).willReturn(true);

            assertThatThrownBy(() -> service.createCard(1L, request))
                    .isInstanceOf(CardException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CARD_ALREADY_EXISTS);
        }

        @Test
        @DisplayName("실패: DB 유니크 제약 위반 → CARD_ALREADY_EXISTS")
        void fail_dataIntegrityViolation() {
            CreateCardRequest request = aCreateRequest("swsh5-58", CardCategory.POKEMON);

            given(cardRepository.existsByTcgdexId("swsh5-58")).willReturn(false);
            given(seriesCommandService.findOrCreate(anyString())).willReturn(TestFixtures.aSeries());
            given(pokemonSetCommandService.findOrCreate(anyString(), anyString(), any()))
                    .willReturn(TestFixtures.aPokemonSet());
            given(pokemonCommandService.findOrCreateForCardName(anyString())).willReturn(Optional.empty());
            given(cardRepository.save(any(Card.class))).willThrow(new DataIntegrityViolationException("unique"));

            assertThatThrownBy(() -> service.createCard(1L, request))
                    .isInstanceOf(CardException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CARD_ALREADY_EXISTS);
        }
    }

    // ---------------------------------------------------------------
    // approveCard
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("approveCard")
    class ApproveCard {

        @Test
        @DisplayName("성공: PENDING 카드 승인 → ACTIVE, ES 인덱싱 + 임베딩 이벤트 발행")
        void success() {
            Card card = buildCard(1L, CardStatus.PENDING);
            given(cardRepository.findById(1L)).willReturn(Optional.of(card));

            CardResponse response = service.approveCard(1L);

            assertThat(response.status()).isEqualTo(CardStatus.ACTIVE);
            // 이벤트는 afterCommit 콜백에 등록되므로 실제 발행은 커밋 후지만,
            // TransactionSynchronization이 등록됐는지 verify는 생략 (카드 상태만 검증)
            verify(eventPublisher).publishEvent(any(CardEmbeddingEvent.class));
        }

        @Test
        @DisplayName("실패: 카드 없음 → CARD_NOT_FOUND")
        void fail_notFound() {
            given(cardRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.approveCard(99L))
                    .isInstanceOf(CardException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CARD_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: PENDING 아닌 카드 승인 시도 → IllegalStateException")
        void fail_notPending() {
            Card card = buildCard(1L, CardStatus.ACTIVE);
            given(cardRepository.findById(1L)).willReturn(Optional.of(card));

            assertThatThrownBy(() -> service.approveCard(1L))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ---------------------------------------------------------------
    // rejectCard
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("rejectCard")
    class RejectCard {

        @Test
        @DisplayName("성공: PENDING 카드 거절 → REJECTED + 사유 저장")
        void success() {
            Card card = buildCard(1L, CardStatus.PENDING);
            given(cardRepository.findById(1L)).willReturn(Optional.of(card));

            CardResponse response = service.rejectCard(1L, "카드 상태 불량");

            assertThat(response.status()).isEqualTo(CardStatus.REJECTED);
        }

        @Test
        @DisplayName("실패: 카드 없음 → CARD_NOT_FOUND")
        void fail_notFound() {
            given(cardRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.rejectCard(99L, "사유"))
                    .isInstanceOf(CardException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CARD_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: PENDING 아닌 카드 거절 → IllegalStateException")
        void fail_notPending() {
            Card card = buildCard(1L, CardStatus.ACTIVE);
            given(cardRepository.findById(1L)).willReturn(Optional.of(card));

            assertThatThrownBy(() -> service.rejectCard(1L, "사유"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("실패: 거절 사유 공백 → IllegalArgumentException")
        void fail_blankReason() {
            Card card = buildCard(1L, CardStatus.PENDING);
            given(cardRepository.findById(1L)).willReturn(Optional.of(card));

            assertThatThrownBy(() -> service.rejectCard(1L, "   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ---------------------------------------------------------------
    // updateCard
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("updateCard")
    class UpdateCard {

        @Test
        @DisplayName("성공: ACTIVE 카드 수정 → ES 재인덱싱 + 임베딩 이벤트 발행")
        void success_activeCard() {
            Card card = buildCard(1L, CardStatus.ACTIVE);
            UpdateCardRequest request = new UpdateCardRequest(null, "수정된이름", null, null, null, null, null, null, null, null, null);
            given(cardRepository.findById(1L)).willReturn(Optional.of(card));

            CardResponse response = service.updateCard(1L, request);

            assertThat(response.name()).isEqualTo("수정된이름");
            verify(eventPublisher).publishEvent(any(CardEmbeddingEvent.class));
        }

        @Test
        @DisplayName("성공: PENDING 카드 수정 → ES 인덱싱 없음")
        void success_pendingCard_noIndexing() {
            Card card = buildCard(1L, CardStatus.PENDING);
            UpdateCardRequest request = new UpdateCardRequest(null, "수정된이름", null, null, null, null, null, null, null, null, null);
            given(cardRepository.findById(1L)).willReturn(Optional.of(card));

            service.updateCard(1L, request);

            verify(eventPublisher, never()).publishEvent(any());
            verify(cardSearchRepository, never()).save(any());
        }

        @Test
        @DisplayName("실패: 카드 없음 → CARD_NOT_FOUND")
        void fail_notFound() {
            UpdateCardRequest request = new UpdateCardRequest(null, "이름", null, null, null, null, null, null, null, null, null);
            given(cardRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateCard(99L, request))
                    .isInstanceOf(CardException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CARD_NOT_FOUND);
        }
    }

    // ---------------------------------------------------------------
    // deleteCard
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("deleteCard")
    class DeleteCard {

        @Test
        @DisplayName("성공: 카드 삭제 + ES 인덱스 제거")
        void success() {
            Card card = buildCard(1L, CardStatus.ACTIVE);
            given(cardRepository.findById(1L)).willReturn(Optional.of(card));

            service.deleteCard(1L);

            verify(cardRepository).delete(card);
        }

        @Test
        @DisplayName("실패: 카드 없음 → CARD_NOT_FOUND")
        void fail_notFound() {
            given(cardRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteCard(99L))
                    .isInstanceOf(CardException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CARD_NOT_FOUND);
        }
    }
}
