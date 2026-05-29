package com.rocketcrew.pocat.domain.order.service;

import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.domain.order.dto.response.CardAveragePriceResponse;
import com.rocketcrew.pocat.domain.order.dto.response.OrderDetailResponse;
import com.rocketcrew.pocat.domain.order.dto.response.OrderResponse;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.OrderException;
import com.rocketcrew.pocat.global.exception.domain.PaymentException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final CardRepository cardRepository;
    private final UserRepository userRepository;

    public Page<OrderResponse> getMyOrders(Long buyerId, OrderStatus status, Pageable pageable) {
        Page<Order> orders = (status != null)
                ? orderRepository.findByBuyerIdAndStatus(buyerId, status, pageable)
                : orderRepository.findByBuyerId(buyerId, pageable);

        List<Long> cardIds = orders.stream()
                .map(Order::getCardId)
                .collect(Collectors.toList());
        Map<Long, Card> cardMap = cardRepository.findAllById(cardIds).stream()
                .collect(Collectors.toMap(Card::getId, Function.identity()));

        return orders.map(order -> {
            Card card = cardMap.get(order.getCardId());
            if (card == null) throw new OrderException(ErrorCode.CARD_NOT_FOUND);
            return OrderResponse.of(order, card.getName(), card.getGrade().name(), card.getImageUrl());
        });
    }

    public OrderDetailResponse getOneOrder(Long userId, String orderUid) {
        Order order = orderRepository.findByOrderUid(orderUid)
                .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.getBuyerId().equals(userId)) {
            throw new OrderException(ErrorCode.ORDER_FORBIDDEN);
        }
        Map<Long, User> userMap = userRepository.findAllById(List.of(order.getBuyerId(), order.getSellerId()))
                .stream().collect(Collectors.toMap(User::getId, u -> u));
        User buyer = userMap.get(order.getBuyerId());
        if (buyer == null) throw new OrderException(ErrorCode.USER_NOT_FOUND);
        User seller = userMap.get(order.getSellerId());
        if (seller == null) throw new OrderException(ErrorCode.USER_NOT_FOUND);
        Card card = cardRepository.findById(order.getCardId())
                .orElseThrow(() -> new OrderException(ErrorCode.CARD_NOT_FOUND));
        return OrderDetailResponse.of(order, buyer, seller, card);
    }

    public CardAveragePriceResponse getAveragePriceByCard(Long cardId) {
        cardRepository.findById(cardId)
                .orElseThrow(() -> new OrderException(ErrorCode.CARD_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusMonths(6);
        Object[] result = orderRepository.findAvgAndCountByCardId(cardId, OrderStatus.ORDER_COMPLETED, since);
        Double avg = (Double) result[0];
        long count = (Long) result[1];
        Long averagePrice = avg != null ? Math.round(avg) : null;
        return new CardAveragePriceResponse(cardId, averagePrice, count, since, now);
    }

    public Order findByOrderid(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new PaymentException(ErrorCode.ORDER_NOT_FOUND));
    }

    public Order findByOrderUid(String orderUid) {
        return orderRepository.findByOrderUid(orderUid)
                .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND));
    }

    @Transactional
    public Order findByOrderIdWithLock(Long orderId) {
        return orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new PaymentException(ErrorCode.ORDER_NOT_FOUND));
    }
}
