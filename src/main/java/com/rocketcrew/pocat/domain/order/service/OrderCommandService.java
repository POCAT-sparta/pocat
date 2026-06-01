package com.rocketcrew.pocat.domain.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
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
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.OrderException;
import com.rocketcrew.pocat.global.outbox.service.OutboxEventWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrderCommandService {

    private final OrderRepository orderRepository;
    private final CardRepository cardRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxEventWriter outboxEventWriter;
    private final AuctionBidRepository auctionBidRepository;
    private final SetExpireService setExpireService;
    private final AuctionRepository auctionRepository;

    // 경매 낙찰 주문 생성 — bidderRank 순위의 Order 저장 후 order.created 이벤트 발행
    public void createOrderFromAuction(Long auctionId, Long cardId, Long sellerId,
                                       Long winnerId, Long finalPrice, int bidderRank) {
        if (orderRepository.findByAuctionIdAndBidderRank(auctionId, bidderRank).isPresent()) {
            return; // 중복 소비 방지
        }
        Order order = orderRepository.save(Order.fromAuction(auctionId, cardId, sellerId, winnerId, finalPrice, bidderRank));

        OrderCreatedEvent event = new OrderCreatedEvent(
                order.getOrderUid(),
                order.getBuyerId(),
                order.getSellerId(),
                order.getFinalPrice()
        );
        outboxEventWriter.write("order", order.getOrderUid(), event);
        eventPublisher.publishEvent(event);
    }

    // 즉시구매 주문만 생성한다. 자동결제는 주문 커밋 이후 상위 유스케이스에서 호출한다.
    public Order createOrderFromBuyout(Long auctionId, Long cardId, Long sellerId,
                                       Long buyerId, Long finalPrice) {
        return orderRepository.save(Order.fromBuyout(auctionId, cardId, sellerId, buyerId, finalPrice));
    }

    // 자동결제 실패 시 1시간 직접결제 창 설정 — 주문 상태를 AUTO_PAYMENT_FAILED로 변경하고 Redis 만료 키 등록
    public void schedulePaymentDeadline(String orderUid) {
        Order order = orderRepository.findByOrderUid(orderUid)
                .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND));
        // 중복 수신 시 이미 AUTO_PAYMENT_FAILED면 스킵 (멱등 처리)
        if (order.getStatus() == OrderStatus.AUTO_PAYMENT_FAILED) {
            log.info("[schedulePaymentDeadline] 이미 처리된 주문 orderUid={}", orderUid);
            return;
        }
        LocalDateTime deadline = LocalDateTime.now().plusHours(1);
        order.startDirectPayment(deadline);
        setExpireService.scheduleExpiry(order.getId(), Duration.ofHours(1));
    }

    // 결제 실패(직접결제 실패 또는 TTL 만료) 시 다음 순위 입찰자에게 1시간 직접결제 기간 부여 (최대 2등까지만)
    public EscalationResult escalateToNextRankWithDirectPayment(String orderUid) {
        Order order = orderRepository.findByOrderUid(orderUid).orElse(null);
        if (order == null) {
            log.warn("[EscalateDirectPayment] 주문 없음 orderUid={}", orderUid);
            return EscalationResult.skipped();
        }
        // 결제 완료된 주문은 승격 차단 (completePayment와 Redis 만료 동시 발화 시 오주문 방지)
        if (order.getStatus() != OrderStatus.AUTO_PAYMENT_FAILED
                && order.getStatus() != OrderStatus.DIRECT_PAYMENT_FAILED) {
            log.info("[EscalateDirectPayment] 승격 불가 상태 orderUid={}, status={}", orderUid, order.getStatus());
            setExpireService.cancelExpiry(order.getId());
            return EscalationResult.skipped();
        }
        if (order.getBidderRank() == null) {
            log.warn("[EscalateDirectPayment] bidderRank 없음(즉시구매) orderUid={}", orderUid);
            setExpireService.cancelExpiry(order.getId());
            return EscalationResult.skipped();
        }

        setExpireService.cancelExpiry(order.getId());

        int nextRank = order.getBidderRank() + 1;

        // 2등까지만 승격 허용
        if (nextRank > 2) {
            cancelAuction(order.getAuctionId());
            log.info("[EscalateDirectPayment] 최대 승격 순위 초과 → 경매 취소 auctionId={}", order.getAuctionId());
            return EscalationResult.cancelled();
        }

        List<Long> lostBidderIds = auctionBidRepository
                .findLostBidderIdsByAuctionIdOrderedByMaxBidPrice(order.getAuctionId());
        int nextBidderIndex = nextRank - 2;
        if (nextBidderIndex >= lostBidderIds.size()) {
            cancelAuction(order.getAuctionId());
            log.info("[EscalateDirectPayment] 다음 입찰자 없음 → 경매 취소 auctionId={}", order.getAuctionId());
            return EscalationResult.cancelled();
        }

        Long nextBidderId = lostBidderIds.get(nextBidderIndex);

        // 멱등성: 해당 순위 주문이 이미 존재하면 스킵
        Optional<Order> existingOrder = orderRepository.findByAuctionIdAndBidderRank(order.getAuctionId(), nextRank);
        if (existingOrder.isPresent()) {
            log.info("[EscalateDirectPayment] 이미 주문 존재 auctionId={}, rank={}", order.getAuctionId(), nextRank);
            return EscalationResult.escalated(nextBidderId, existingOrder.get().getOrderUid());
        }

        Order nextOrder = orderRepository.save(
                Order.fromAuction(order.getAuctionId(), order.getCardId(), order.getSellerId(),
                        nextBidderId, order.getFinalPrice(), nextRank));
        schedulePaymentDeadline(nextOrder.getOrderUid());
        log.info("[EscalateDirectPayment] {}순위 1시간 직접결제 기간 부여 auctionId={}, buyerId={}",
                nextRank, order.getAuctionId(), nextBidderId);
        return EscalationResult.escalated(nextBidderId, nextOrder.getOrderUid());
    }

    private void cancelAuction(Long auctionId) {
        auctionRepository.findById(auctionId).ifPresent(auction -> auction.cancel("결제 최종 실패"));
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
