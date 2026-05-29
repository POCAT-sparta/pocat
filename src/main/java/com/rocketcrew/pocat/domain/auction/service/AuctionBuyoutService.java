package com.rocketcrew.pocat.domain.auction.service;

import com.rocketcrew.pocat.domain.auction.dto.BuyoutCompletion;
import com.rocketcrew.pocat.domain.auction.dto.BuyoutReservation;
import com.rocketcrew.pocat.domain.auction.dto.response.BuyoutAuctionResponse;
import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.event.AuctionBuyoutCompletedEvent;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.order.service.OrderCommandService;
import com.rocketcrew.pocat.domain.order.service.OrderQueryService;
import com.rocketcrew.pocat.domain.payment.dto.response.PaymentResponse;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import com.rocketcrew.pocat.domain.payment.repository.PaymentRepository;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.service.UserQueryService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.AuctionException;
import com.rocketcrew.pocat.global.event.BaseEvent;
import com.rocketcrew.pocat.global.outbox.service.OutboxEventWriter;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuctionBuyoutService {

    private static final String AUCTION_LOCK_KEY_PREFIX = "auction:lock:";
    private static final long AUCTION_LOCK_WAIT_SECONDS = 0L;

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderCommandService orderCommandService;
    private final OrderQueryService orderQueryService;
    private final UserQueryService userQueryService;
    private final RedissonClient redissonClient;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxEventWriter outboxEventWriter;
    private final AuctionBuyoutTransactionService buyoutTransactionService;

    // 즉시 구매 전체 흐름을 조율하는 진입 메서드
    public BuyoutAuctionResponse buyout(Long buyerId, Long auctionId) {
        // 구매자 정보 조회
        User buyer = userQueryService.getUserEntity(buyerId);
        // 레디스 락 획득 및 즉시 구매 선점 처리
        BuyoutReservation reservation = reserveBuyoutWithLock(auctionId, buyer);

        PaymentResponse paymentResponse;
        try {
            // 즉시 구매 주문 생성 및 자동결제 요청
            paymentResponse = orderCommandService.createOrderFromBuyout(
                    reservation.auctionId(),
                    reservation.cardId(),
                    reservation.sellerId(),
                    buyerId,
                    reservation.buyoutPrice()
            );
        } catch (RuntimeException e) {
            // 즉시 구매 자동 결제 실패 시 경매 ACTIVE 복구 로직
            buyoutTransactionService.restoreAuctionAfterPaymentFailure(auctionId);
            throw e;    // 사용자에게 즉시 구매 실패 응답
        }
        // 결제 성공시 주문 상태 재조회
        Order order = orderQueryService.findByOrderid(paymentResponse.orderId());
        if (order.getStatus() != OrderStatus.PAYMENT_COMPLETED) {
            buyoutTransactionService.restoreAuctionAfterPaymentFailure(auctionId);
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATUS_TRANSITION);
        }

        // 결제 성공이 확인된 즉시 구매를 경매/입찰 도메인에 최종 반영
        BuyoutCompletion completion = buyoutTransactionService.completeBuyout(
                reservation,
                buyerId,
                order,
                this::publishBuyoutCompletedEvent   // completeBuyout 메서드 안에서 호출할 콜백 메서드
        );

        return BuyoutAuctionResponse.of(completion.auction(), completion.buyoutBid(), order, paymentResponse);
    }

    public boolean recoverStalePaymentPendingAuction(Long auctionId) {
        //   즉시구매 또는 낙찰 주문이 생성되었는지 조회
        return orderRepository.findByAuctionIdAndBidderRank(auctionId, 1)
                //   주문이 있으면 주문/결제 상태를 기준으로 다음 처리 방향을 판단
                .map(order -> recoverStaleBuyoutWithOrder(auctionId, order))
                //   주문이 없으면 주문 생성 전 장애로 보고 경매를 ACTIVE로 복구합니다.
                .orElseGet(() -> buyoutTransactionService.restoreAuctionAfterPaymentFailure(auctionId));
    }

    //  주문이 있는 PAYMENT_PENDING 경매를 결제 성공/실패/대기 상태별로 나눠 처리합니다.
    private boolean recoverStaleBuyoutWithOrder(Long auctionId, Order order) {
        // 결제 엔티티 조회
        Payment payment = paymentRepository.findByOrderId(order.getId()).orElse(null);

        //   주문 또는 결제가 완료 상태인지 확인합니다.
        if (isPaymentCompleted(order, payment)) {
            //   결제가 성공했으면 경매/입찰 확정 처리를 재시도합니다.
            completeRecoveredBuyout(auctionId, order);
            return true;
        }

        //   결제가 실패했거나 결제 엔티티가 없는 상태인지 확인합니다.
        if (isPaymentFailedOrMissing(order, payment)) {
            //   결제 실패 또는 결제 없음이면 경매를 다시 ACTIVE로 복구합니다.
            return buyoutTransactionService.restoreAuctionAfterPaymentFailure(auctionId);
        }

        //   결제가 아직 PENDING이면 결제 도메인 스케줄러가 정리할 때까지 아무것도 하지 않습니다.
        return false;
    }

    private boolean isPaymentCompleted(Order order, Payment payment) {
        return order.getStatus() == OrderStatus.PAYMENT_COMPLETED
                || (payment != null && payment.getStatus() == PaymentStatus.COMPLETED);
    }

    private boolean isPaymentFailedOrMissing(Order order, Payment payment) {
        return payment == null
                || payment.getStatus() == PaymentStatus.FAILED
                || order.getStatus() == OrderStatus.AUTO_PAYMENT_FAILED
                || order.getStatus() == OrderStatus.DIRECT_PAYMENT_FAILED
                || order.getStatus() == OrderStatus.CANCELLED;
    }

    private void completeRecoveredBuyout(Long auctionId, Order order) {
        BuyoutReservation reservation = new BuyoutReservation(
                auctionId,
                order.getCardId(),
                order.getSellerId(),
                order.getFinalPrice()
        );
        buyoutTransactionService.completeBuyout(
                reservation,
                order.getBuyerId(),
                order,
                this::publishBuyoutCompletedEvent
        );
    }

    private BuyoutReservation reserveBuyoutWithLock(Long auctionId, User buyer) {
        // 레디스 분산락 획득
        RLock lock = redissonClient.getLock(AUCTION_LOCK_KEY_PREFIX + auctionId);
        if (!acquireLock(lock)) {
            throw new AuctionException(ErrorCode.AUCTION_LOCK_FAILED);
        }

        try {
            // 경매, 구매자 유효성 검증 및 경매 상태 ACTIVE -> PAYMENT_PENDING으로 변경
            return buyoutTransactionService.reserveBuyout(auctionId, buyer);
        } finally {
            // 레디스 분산락 해제
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    void publishBuyoutCompletedEvent(Auction auction, Order order,
                                     Long buyerId, Long previousHighestBidderId) {
        publishAuctionEvent(auction.getId(), new AuctionBuyoutCompletedEvent(
                auction.getId(),
                order.getId(),
                order.getOrderUid(),
                buyerId,
                auction.getSellerId(),
                auction.getCardId(),
                order.getFinalPrice(),
                previousHighestBidderId
        ));
    }

    private void publishAuctionEvent(Long auctionId, BaseEvent event) {
        outboxEventWriter.write("auction", String.valueOf(auctionId), event);
        eventPublisher.publishEvent(event);
    }

    private boolean acquireLock(RLock lock) {
        try {
            return lock.tryLock(AUCTION_LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuctionException(ErrorCode.AUCTION_LOCK_FAILED, e);
        }
    }
}
