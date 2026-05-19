package com.rocketcrew.pocat.domain.settlement.service;

import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.settlement.dto.response.SettlementResponse;
import com.rocketcrew.pocat.domain.settlement.entity.Settlement;
import com.rocketcrew.pocat.domain.settlement.repository.SettlementRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.CardException;
import com.rocketcrew.pocat.global.exception.domain.OrderException;
import com.rocketcrew.pocat.global.exception.domain.SettlementException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementQueryService {
    private final SettlementRepository settlementRepository;
    private final OrderRepository orderRepository;
    private final CardRepository cardRepository;

    public Page<SettlementResponse> getSettlements(Long sellerId, Pageable pageable) {
        Page<Settlement> settlements = settlementRepository.findBySellerId(sellerId, pageable);

        List<Long> orderIds = settlements.stream().map(Settlement::getOrderId).toList();
        Map<Long, Order> orderMap = orderRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(Order::getId, o -> o));

        List<Long> cardIds = orderMap.values().stream().map(Order::getCardId).toList();
        Map<Long, Card> cardMap = cardRepository.findAllById(cardIds).stream()
                .collect(Collectors.toMap(Card::getId, c -> c));

        return settlements.map(s -> {
            Order order = orderMap.get(s.getOrderId());
            if (order == null) {
                throw new OrderException(ErrorCode.ORDER_NOT_FOUND);
            }
            Card card = cardMap.get(order.getCardId());
            if (card == null) {
                throw new CardException(ErrorCode.CARD_NOT_FOUND);
            }
            return SettlementResponse.from(s, order, card);
        });
    }

    public SettlementResponse getOneSettlement(Long sellerId, String settlementUid) {
        Settlement settlement = settlementRepository.findBySettlementUidAndSellerId(settlementUid, sellerId)
                .orElseThrow(() -> new SettlementException(ErrorCode.SETTLEMENT_NOT_FOUND));

        Order order = orderRepository.findById(settlement.getOrderId())
                .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND));

        Card card = cardRepository.findById(order.getCardId())
                .orElseThrow(() -> new CardException(ErrorCode.CARD_NOT_FOUND));

        return SettlementResponse.from(settlement, order, card);
    }
}
