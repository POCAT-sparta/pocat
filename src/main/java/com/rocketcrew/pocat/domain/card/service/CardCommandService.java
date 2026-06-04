package com.rocketcrew.pocat.domain.card.service;

import com.rocketcrew.pocat.domain.ai.rag.event.CardEmbeddingEvent;
import com.rocketcrew.pocat.domain.card.document.CardDocument;
import com.rocketcrew.pocat.domain.card.dto.request.CreateCardRequest;
import com.rocketcrew.pocat.domain.card.dto.request.UpdateCardRequest;
import com.rocketcrew.pocat.domain.card.dto.response.CardResponse;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.domain.card.repository.CardSearchRepository;
import com.rocketcrew.pocat.domain.pokemon.entity.Pokemon;
import com.rocketcrew.pocat.domain.pokemon.service.PokemonCommandService;
import com.rocketcrew.pocat.domain.series.entity.Series;
import com.rocketcrew.pocat.domain.series.service.SeriesCommandService;
import com.rocketcrew.pocat.domain.set.entity.PokemonSet;
import com.rocketcrew.pocat.domain.set.service.PokemonSetCommandService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.CardException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CardCommandService {

    private final CardRepository cardRepository;
    private final CardSearchRepository cardSearchRepository;
    private final SeriesCommandService seriesCommandService;
    private final PokemonSetCommandService pokemonSetCommandService;
    private final PokemonCommandService pokemonCommandService;
    private final ApplicationEventPublisher eventPublisher;

    public CardResponse createCard(Long userId, CreateCardRequest request) {
        if (request.tcgdexId() != null && cardRepository.existsByTcgdexId(request.tcgdexId())) {
            throw new CardException(ErrorCode.CARD_ALREADY_EXISTS);
        }

        Series series = seriesCommandService.findOrCreate(request.series());
        PokemonSet pokemonSet = pokemonSetCommandService.findOrCreate(
                request.setId(), request.setName(), series);
        Pokemon pokemon = null;
        if (request.category() == CardCategory.POKEMON) {
            pokemon = pokemonCommandService.findOrCreateForCardName(request.name()).orElse(null);
        }

        Card card = Card.builder()
                .userId(userId)
                .tcgdexId(request.tcgdexId())
                .name(request.name())
                .series(series)
                .pokemonSet(pokemonSet)
                .pokemon(pokemon)
                .cardNumber(request.cardNumber())
                .rarity(request.rarity())
                .category(request.category())
                .grade(request.grade())
                .imageUrl(request.imageUrl())
                .source(request.source())
                .status(CardStatus.PENDING)
                .build();
        try {
            return CardResponse.from(cardRepository.save(card));
        } catch (DataIntegrityViolationException e) {
            throw new CardException(ErrorCode.CARD_ALREADY_EXISTS);
        }
    }

    public CardResponse updateCard(Long id, UpdateCardRequest request) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new CardException(ErrorCode.CARD_NOT_FOUND));

        Series series = null;
        if (request.series() != null) {
            series = seriesCommandService.findOrCreate(request.series());
        }
        PokemonSet pokemonSet = null;
        if (request.setId() != null) {
            String setName = request.setName() != null ? request.setName()
                    : (card.getPokemonSet() != null ? card.getPokemonSet().getName() : request.setId());
            pokemonSet = pokemonSetCommandService.findOrCreate(request.setId(), setName, series);
        }

        card.update(request.tcgdexId(), request.name(), series, pokemonSet,
                request.cardNumber(), request.rarity(), request.category(),
                request.grade(), request.imageUrl(), request.source());

        if (card.getStatus() == CardStatus.ACTIVE) {
            indexCard(card);
            String cardText = card.getName() + " " + card.getGrade() + " " + card.getSeries();
            eventPublisher.publishEvent(new CardEmbeddingEvent(card.getId(), cardText));
        }
        return CardResponse.from(card);
    }

    public void deleteCard(Long id) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new CardException(ErrorCode.CARD_NOT_FOUND));
        cardRepository.delete(card);
        deleteCardIndex(id);
    }

    public CardResponse approveCard(Long id) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new CardException(ErrorCode.CARD_NOT_FOUND));
        card.approve();
        indexCard(card);

        // Publish embedding event for RAG after card approval
        String cardText = card.getName() + " " + card.getGrade() + " " + card.getSeries();
        eventPublisher.publishEvent(new CardEmbeddingEvent(card.getId(), cardText));

        return CardResponse.from(card);
    }

    public CardResponse rejectCard(Long id, String rejectReason) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new CardException(ErrorCode.CARD_NOT_FOUND));
        card.reject(rejectReason);
        return CardResponse.from(card);
    }

    void indexCard(Card card) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doIndexCard(card);
                }
            });
        } else {
            doIndexCard(card);
        }
    }

    private void doIndexCard(Card card) {
        try {
            String nameKo    = card.getPokemon() != null ? card.getPokemon().getNameKo() : null;
            String seriesKo  = card.getSeries() != null ? card.getSeries().getNameKo() : null;
            String setNameKo = card.getPokemonSet() != null ? card.getPokemonSet().getNameKo() : null;
            cardSearchRepository.save(CardDocument.from(card, nameKo, seriesKo, setNameKo));
        } catch (Exception e) {
            log.warn("[ES_INDEXING] 카드 인덱싱 실패 cardId={}: {}", card.getId(), e.getMessage());
        }
    }

    private void deleteCardIndex(Long id) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doDeleteCardIndex(id);
                }
            });
        } else {
            doDeleteCardIndex(id);
        }
    }

    private void doDeleteCardIndex(Long id) {
        try {
            cardSearchRepository.deleteById(String.valueOf(id));
        } catch (Exception e) {
            log.warn("[ES_INDEXING] 카드 인덱스 삭제 실패 cardId={}: {}", id, e.getMessage());
        }
    }
}
