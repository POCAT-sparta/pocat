package com.rocketcrew.pocat.domain.card.service;

import com.rocketcrew.pocat.domain.card.dto.request.CreateCardRequest;
import com.rocketcrew.pocat.domain.card.dto.request.UpdateCardRequest;
import com.rocketcrew.pocat.domain.card.dto.response.CardResponse;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.CardException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CardService {

    private final CardRepository cardRepository;

    @Transactional(readOnly = true)
    public Page<CardResponse> getCards(Pageable pageable) {
        return cardRepository.findAll(pageable)
                .map(CardResponse::from);
    }

    @Transactional(readOnly = true)
    public CardResponse getCard(Long id) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new CardException(ErrorCode.CARD_NOT_FOUND));
        return CardResponse.from(card);
    }

    public CardResponse createCard(Long userId, CreateCardRequest request) {
        Card card = Card.builder()
                .userId(userId)
                .tcgdexId(request.tcgdexId())
                .name(request.name())
                .series(request.series())
                .setName(request.setName())
                .cardNumber(request.cardNumber())
                .rarity(request.rarity())
                .grade(request.grade())
                .imageUrl(request.imageUrl())
                .source(request.source())
                .status(CardStatus.PENDING)
                .build();
        Card saved = cardRepository.save(card);
        return CardResponse.from(saved);
    }

    public CardResponse updateCard(Long id, UpdateCardRequest request) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new CardException(ErrorCode.CARD_NOT_FOUND));
        card.update(
                request.tcgdexId(),
                request.name(),
                request.series(),
                request.setName(),
                request.cardNumber(),
                request.rarity(),
                request.grade(),
                request.imageUrl(),
                request.source()
        );
        return CardResponse.from(card);
    }

    public void deleteCard(Long id) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new CardException(ErrorCode.CARD_NOT_FOUND));
        cardRepository.delete(card);
    }
}
