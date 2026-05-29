package com.rocketcrew.pocat.domain.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.domain.order.dto.response.OrderResponse;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.DeliveryStatus;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.event.OrderCancelledEvent;
import com.rocketcrew.pocat.domain.order.event.OrderCreatedEvent;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.payment.dto.response.PaymentResponse;
import com.rocketcrew.pocat.domain.payment.service.PaymentApplicationService;
import com.rocketcrew.pocat.domain.payment.service.PaymentCommandService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.OrderException;
import com.rocketcrew.pocat.global.outbox.entity.OutboxEvent;
import com.rocketcrew.pocat.global.outbox.repository.OutboxRepository;
import com.rocketcrew.pocat.global.outbox.service.OutboxEventWriter;
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
    private final PaymentApplicationService paymentApplicationService;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final OutboxEventWriter outboxEventWriter;

    // 경매 낙찰 주문 생성 — rank=1 Order 저장 후 order.created 이벤트 발행
    // Payment 도메인이 이벤트를 컨슘해 자동결제 처리
    public void createOrderFromAuction(Long auctionId, Long cardId, Long sellerId,
                                       Long winnerId, Long finalPrice) {
        if (orderRepository.findByAuctionIdAndBidderRank(auctionId, 1).isPresent()) {
            return; // 중복 소비 방지
        }
        Order order = orderRepository.save(
                Order.fromAuction(auctionId, cardId, sellerId, winnerId, finalPrice, 1));

        OrderCreatedEvent event = new OrderCreatedEvent(
                order.getOrderUid(),
                order.getBuyerId(),
                order.getSellerId(),
                order.getFinalPrice()
        );
        outboxEventWriter.write("order", order.getOrderUid(), event);
        eventPublisher.publishEvent(event);
    }

    // 즉시구매 주문 생성 — 자동결제 시도, 실패 시 재시도 기회 없이 즉시 종료
    public PaymentResponse createOrderFromBuyout(Long auctionId, Long cardId, Long sellerId,
                                                 Long buyerId, Long finalPrice) {
        Order order = orderRepository.save(
                Order.fromBuyout(auctionId, cardId, sellerId, buyerId, finalPrice));
        return paymentApplicationService.autoPayment(order.getId());
    }

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
