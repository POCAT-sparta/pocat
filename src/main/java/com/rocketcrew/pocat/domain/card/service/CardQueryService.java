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
import org.springframework.util.StringUtils;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CardQueryService {

    private final CardRepository cardRepository;
    private final OrderQueryService orderQueryService;

    public Page<CardResponse> getCards(CardSearchCondition condition, Pageable pageable) {
        if (StringUtils.hasText(condition.keyword()) && condition.keyword().trim().length() < 2) {
            throw new CardException(ErrorCode.INVALID_INPUT);
        }
        return cardRepository.searchCards(condition, pageable)
                .map(CardResponse::from);
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
        return orderQueryService.getAveragePriceByCard(cardId);
    }
    public void validateRegistrableForAuction(Long cardId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new CardException(ErrorCode.CARD_NOT_FOUND));
        if (card.getStatus() != CardStatus.ACTIVE) {
            throw new CardException(ErrorCode.CARD_NOT_ACTIVE);
        }
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
