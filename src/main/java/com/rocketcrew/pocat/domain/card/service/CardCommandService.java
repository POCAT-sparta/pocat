package com.rocketcrew.pocat.domain.card.service;

import com.rocketcrew.pocat.domain.card.dto.request.CreateCardRequest;
import com.rocketcrew.pocat.domain.card.dto.request.UpdateCardRequest;
import com.rocketcrew.pocat.domain.card.dto.response.CardResponse;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.CardException;
import org.springframework.dao.DataIntegrityViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CardCommandService {

    private final CardRepository cardRepository;

    public CardResponse createCard(Long userId, CreateCardRequest request) {
        if (request.tcgdexId() != null && cardRepository.existsByTcgdexId(request.tcgdexId())) {
            throw new CardException(ErrorCode.CARD_ALREADY_EXISTS);
        }
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
        try {
            return CardResponse.from(cardRepository.save(card));
        } catch (DataIntegrityViolationException e) {
            throw new CardException(ErrorCode.CARD_ALREADY_EXISTS);
        }
    }

    public CardResponse updateCard(Long id, UpdateCardRequest request) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new CardException(ErrorCode.CARD_NOT_FOUND));
        card.update(
                request.tcgdexId(),
                request.name(),
                request.series(),
                request.setId(),
                request.setName(),
                request.cardNumber(),
                request.rarity(),
                request.category(),
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

    public CardResponse approveCard(Long id) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new CardException(ErrorCode.CARD_NOT_FOUND));
        card.approve();
        return CardResponse.from(card);
    }

    public CardResponse rejectCard(Long id, String rejectReason) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new CardException(ErrorCode.CARD_NOT_FOUND));
        card.reject(rejectReason);
        return CardResponse.from(card);
    }
}
