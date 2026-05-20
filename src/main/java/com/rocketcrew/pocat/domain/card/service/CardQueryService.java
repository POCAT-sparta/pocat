package com.rocketcrew.pocat.domain.card.service;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CardQueryService {

    private final CardRepository cardRepository;
    private final OrderQueryService orderQueryService;

    public Page<CardResponse> getCards(CardSearchCondition condition, Pageable pageable) {
        return cardRepository.searchCards(condition, pageable)
                .map(CardResponse::from);
    }

    public CardResponse getCard(Long id) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new CardException(ErrorCode.CARD_NOT_FOUND));
        return CardResponse.from(card);
    }

    public CardAveragePriceResponse getAveragePrice(Long cardId) {
        return orderQueryService.getAveragePriceByCard(cardId);
    }
    public Card validateRegistrableForAuction(Long cardId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new CardException(ErrorCode.CARD_NOT_FOUND));
        if (card.getStatus() != CardStatus.ACTIVE) {
            throw new CardException(ErrorCode.CARD_NOT_ACTIVE);
        }
        return card;
    }

    public List<Long> searchCardIds(CardSearchCondition condition) {
        return cardRepository.searchCardIds(condition);
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
