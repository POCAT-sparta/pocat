package com.rocketcrew.pocat.domain.order.service;

import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.domain.order.dto.response.OrderResponse;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.DeliveryStatus;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.event.OrderCancelledEvent;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.CardException;
import com.rocketcrew.pocat.global.exception.domain.OrderException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderCommandService {

    private final OrderRepository orderRepository;
    private final CardRepository cardRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderResponse cancelOrder(Long userId, String orderUid, String reason) {
        Order order = orderRepository.findByOrderUid(orderUid)
                .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getBuyerId().equals(userId)) {
            throw new OrderException(ErrorCode.ORDER_FORBIDDEN);
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new OrderException(ErrorCode.ORDER_ALREADY_CANCELLED);
        }
        if (order.getStatus() == OrderStatus.REFUNDED) {
            throw new OrderException(ErrorCode.ORDER_CANNOT_CANCEL);
        }
        if (order.getDeliveryStatus() == DeliveryStatus.SHIPPING) {
            throw new OrderException(ErrorCode.ORDER_CANNOT_CANCEL);
        }

        Card card = cardRepository.findById(order.getCardId())
                .orElseThrow(() -> new OrderException(ErrorCode.CARD_NOT_FOUND));
        order.cancel(reason);

        // 주문 취소 이벤트 발행
        eventPublisher.publishEvent(new OrderCancelledEvent(
                order.getOrderUid(),
                order.getBuyerId(),
                order.getSellerId(),
                reason
        ));

        return OrderResponse.of(order, card.getName(), card.getGrade().name(), card.getImageUrl());
    }
}
