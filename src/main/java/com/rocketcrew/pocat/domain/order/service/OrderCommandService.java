package com.rocketcrew.pocat.domain.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.bid.repository.AuctionBidRepository;
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
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.OrderException;
import com.rocketcrew.pocat.global.outbox.service.OutboxEventWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrderCommandService {

    private final OrderRepository orderRepository;
    private final CardRepository cardRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentApplicationService paymentApplicationService;
    private final OutboxEventWriter outboxEventWriter;
    private final OrderSaveService orderSaveService;
    private final AuctionBidRepository auctionBidRepository;
    private final SetExpireService setExpireService;

    // 경매 낙찰 주문 생성 — bidderRank 순위의 Order 저장 후 order.created 이벤트 발행
    public void createOrderFromAuction(Long auctionId, Long cardId, Long sellerId,
                                       Long winnerId, Long finalPrice, int bidderRank) {
        if (orderRepository.findByAuctionIdAndBidderRank(auctionId, bidderRank).isPresent()) {
            return; // 중복 소비 방지
        }
        Order order = orderRepository.save(
                Order.fromAuction(auctionId, cardId, sellerId, winnerId, finalPrice, bidderRank));

        OrderCreatedEvent event = new OrderCreatedEvent(
                order.getOrderUid(),
                order.getBuyerId(),
                order.getSellerId(),
                order.getFinalPrice()
        );
        outboxEventWriter.write("order", order.getOrderUid(), event);
        eventPublisher.publishEvent(event);
    }

    // 즉시구매 주문 생성 — 주문 저장(별도 트랜잭션) 후 자동결제 시도
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentResponse createOrderFromBuyout(Long auctionId, Long cardId, Long sellerId,
                                                 Long buyerId, Long finalPrice) {
        Order order = orderSaveService.saveBuyoutOrder(auctionId, cardId, sellerId, buyerId, finalPrice);
        return paymentApplicationService.autoPayment(order.getOrderUid());
    }

    // 결제 실패(직접결제 실패 또는 TTL 만료) 시 다음 순위 입찰자에게 주문 생성. 다음 입찰자가 있으면 true, 없으면 false
    public boolean escalateToNextRank(String orderUid) {
        Order order = orderRepository.findByOrderUid(orderUid).orElse(null);
        if (order == null) {
            log.warn("[EscalateToNextRank] 주문 없음 orderUid={}", orderUid);
            return false;
        }

        // 결제 완료된 주문은 승격 차단 (completePayment와 Redis 만료 동시 발화 시 오주문 방지)
        if (order.getStatus() != OrderStatus.AUTO_PAYMENT_FAILED
                && order.getStatus() != OrderStatus.DIRECT_PAYMENT_FAILED) {
            log.info("[EscalateToNextRank] 승격 불가 상태 orderUid={}, status={}", orderUid, order.getStatus());
            setExpireService.cancelExpiry(order.getId());
            return false;
        }

        // 즉시구매 주문은 bidderRank가 null이므로 승격 불가
        if (order.getBidderRank() == null) {
            log.warn("[EscalateToNextRank] bidderRank 없음(즉시구매 주문) orderUid={}", orderUid);
            setExpireService.cancelExpiry(order.getId());
            return false;
        }

        setExpireService.cancelExpiry(order.getId());

        int nextRank = order.getBidderRank() + 1;
        List<Long> lostBidderIds = auctionBidRepository
                .findLostBidderIdsByAuctionIdOrderedByMaxBidPrice(order.getAuctionId());

        int nextBidderIndex = nextRank - 2;
        if (nextBidderIndex >= lostBidderIds.size()) {
            log.info("[EscalateToNextRank] 다음 입찰자 없음 auctionId={}, currentRank={}",
                    order.getAuctionId(), order.getBidderRank());
            return false;
        }

        Long nextBidderId = lostBidderIds.get(nextBidderIndex);
        createOrderFromAuction(order.getAuctionId(), order.getCardId(), order.getSellerId(),
                nextBidderId, order.getFinalPrice(), nextRank);
        log.info("[EscalateToNextRank] {}순위 주문 생성 auctionId={}, buyerId={}",
                nextRank, order.getAuctionId(), nextBidderId);
        return true;
    }

    // 자동결제 실패 시 1시간 직접결제 창 설정 — 주문 상태를 AUTO_PAYMENT_FAILED로 변경하고 Redis 만료 키 등록
    public void schedulePaymentDeadline(String orderUid) {
        Order order = orderRepository.findByOrderUid(orderUid)
                .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND));
        LocalDateTime deadline = LocalDateTime.now().plusHours(1);
        order.startDirectPayment(deadline);
        setExpireService.scheduleExpiry(order.getId(), Duration.ofHours(1));
    }

    public void failDirectPayment(String orderUid) {
        Order order = orderRepository.findByOrderUid(orderUid)
                .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND));
        order.failPayment();
    }

    public void completePayment(String orderUid) {
        Order order = orderRepository.findByOrderUid(orderUid)
                .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND));
        // 이미 완료된 건은 리턴 (createSnapshot 실패 후 Kafka 재처리 시 멱등 보장)
        if (order.getStatus() == OrderStatus.PAYMENT_COMPLETED) return;
        order.completePayment();
        // 직접결제 창 만료 키 취소 — 미취소 시 1시간 후 ExpiryEventListener가 완료된 주문을 다음 순위로 잘못 승격
        setExpireService.cancelExpiry(order.getId());
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
