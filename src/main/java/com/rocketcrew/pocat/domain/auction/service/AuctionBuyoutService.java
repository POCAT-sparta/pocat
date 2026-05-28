package com.rocketcrew.pocat.domain.auction.service;

import com.rocketcrew.pocat.domain.auction.dto.response.BuyoutAuctionResponse;
import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.event.AuctionBuyoutCompletedEvent;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.bid.entity.AuctionBid;
import com.rocketcrew.pocat.domain.bid.enums.BidStatus;
import com.rocketcrew.pocat.domain.bid.event.BidOutbidEvent;
import com.rocketcrew.pocat.domain.bid.repository.AuctionBidRepository;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.service.OrderCommandService;
import com.rocketcrew.pocat.domain.order.service.OrderQueryService;
import com.rocketcrew.pocat.domain.payment.dto.response.PaymentResponse;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.service.UserQueryService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.AuctionException;
import com.rocketcrew.pocat.global.exception.domain.BidException;
import com.rocketcrew.pocat.global.event.BaseEvent;
import com.rocketcrew.pocat.global.outbox.service.OutboxEventWriter;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Transactional
public class AuctionBuyoutService {

    private static final String AUCTION_LOCK_KEY_PREFIX = "auction:lock:";
    private static final long AUCTION_LOCK_WAIT_SECONDS = 0L;
    private static final ZoneId AUCTION_ZONE = ZoneId.of("Asia/Seoul");

    private final AuctionRepository auctionRepository;
    private final AuctionBidRepository auctionBidRepository;
    private final OrderCommandService orderCommandService;
    private final OrderQueryService orderQueryService;
    private final UserQueryService userQueryService;
    private final EntityManager entityManager;
    private final RedissonClient redissonClient;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxEventWriter outboxEventWriter;

    public BuyoutAuctionResponse buyout(Long buyerId, Long auctionId) {
        User buyer = userQueryService.getUserEntity(buyerId);
        Auction auction = findAuction(auctionId);

        validateBuyoutAvailable(auction);
        validateBuyer(buyer, auction);

        RLock lock = redissonClient.getLock(AUCTION_LOCK_KEY_PREFIX + auctionId);
        if (!acquireLock(lock)) {
            throw new AuctionException(ErrorCode.AUCTION_LOCK_FAILED);
        }
        releaseLockAfterTransaction(lock);

        entityManager.detach(auction);
        Auction latestAuction = findAuction(auctionId);

        validateBuyoutAvailable(latestAuction);
        validateBuyer(buyer, latestAuction);

        Long buyoutPrice = latestAuction.getBuyoutPrice();
        latestAuction.markPaymentPending();

        PaymentResponse paymentResponse;
        try {
            paymentResponse = orderCommandService.createOrderFromBuyout(
                    latestAuction.getId(),
                    latestAuction.getCardId(),
                    latestAuction.getSellerId(),
                    buyerId,
                    buyoutPrice
            );
        } catch (RuntimeException e) {
            latestAuction.restoreActiveFromPaymentPending();
            throw e;
        }

        Order order = orderQueryService.findByOrderid(paymentResponse.orderId());

        if (order.getStatus() != OrderStatus.PAYMENT_COMPLETED) {
            latestAuction.restoreActiveFromPaymentPending();
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATUS_TRANSITION);
        }

        Long previousHighestBidderId = latestAuction.getHighestBidderId();
        AuctionBid buyoutBid = auctionBidRepository.save(AuctionBid.builder()
                .auctionId(latestAuction.getId())
                .userId(buyerId)
                .bidPrice(buyoutPrice)
                .status(BidStatus.LEADING)
                .build());
        buyoutBid.markWon();

        markExistingBidsLost(latestAuction, buyerId);
        latestAuction.updateHighestBid(buyoutPrice, buyerId);
        latestAuction.endAfterPaymentPending();
        publishBidOutbidEventIfNeeded(latestAuction.getId(), previousHighestBidderId, buyoutPrice);
        publishBuyoutCompletedEvent(latestAuction, order, buyerId, previousHighestBidderId);

        return BuyoutAuctionResponse.of(latestAuction, buyoutBid, order, paymentResponse);
    }

    private Auction findAuction(Long auctionId) {
        return auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionException(ErrorCode.AUCTION_NOT_FOUND));
    }

    private void validateBuyoutAvailable(Auction auction) {
        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            throw new AuctionException(ErrorCode.AUCTION_NOT_ACTIVE);
        }

        LocalDateTime now = LocalDateTime.now(AUCTION_ZONE);
        if (auction.getStartedAt() == null
                || auction.getEndedAt() == null
                || now.isBefore(auction.getStartedAt())
                || !now.isBefore(auction.getEndedAt())) {
            throw new AuctionException(ErrorCode.AUCTION_NOT_ACTIVE);
        }

        if (auction.getBuyoutPrice() == null) {
            throw new AuctionException(ErrorCode.AUCTION_PRICE_INVALID);
        }
    }

    private void validateBuyer(User buyer, Auction auction) {
        if (buyer.isBidBlocked()) {
            throw new BidException(ErrorCode.BID_BLOCKED_USER);
        }
        if (auction.getSellerId().equals(buyer.getId())) {
            throw new BidException(ErrorCode.BID_SELLER_FORBIDDEN);
        }
        if (!StringUtils.hasText(buyer.getBillingKey())) {
            throw new BidException(ErrorCode.BID_BILLING_KEY_REQUIRED);
        }
    }

    private void markExistingBidsLost(Auction auction, Long buyoutBuyerId) {
        for (AuctionBid bid : auctionBidRepository.findAllByAuctionId(auction.getId())) {
            if (bid.getUserId().equals(buyoutBuyerId)) {
                continue;
            }
            if (bid.getStatus() == BidStatus.LEADING) {
                bid.markOutbid();
                bid.markLost();
            } else if (bid.getStatus() == BidStatus.OUTBID) {
                bid.markLost();
            }
        }
    }

    private void publishBuyoutCompletedEvent(Auction auction, Order order,
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

    private void publishBidOutbidEventIfNeeded(Long auctionId, Long previousHighestBidderId,
                                               Long currentHighestPrice) {
        if (previousHighestBidderId == null) {
            return;
        }
        publishBidEvent(auctionId, new BidOutbidEvent(
                auctionId,
                previousHighestBidderId,
                currentHighestPrice
        ));
    }

    private void publishBidEvent(Long auctionId, BaseEvent event) {
        outboxEventWriter.write("bid", String.valueOf(auctionId), event);
        eventPublisher.publishEvent(event);
    }

    private boolean acquireLock(RLock lock) {
        try {
            return lock.tryLock(AUCTION_LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuctionException(ErrorCode.AUCTION_LOCK_FAILED);
        }
    }

    private void releaseLockAfterTransaction(RLock lock) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        });
    }
}
