package com.rocketcrew.pocat.internal.testscenario.auction.service;

import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.redis.AuctionExpirationRedisService;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.auction.service.AuctionEsIndexService;
import com.rocketcrew.pocat.domain.bid.entity.AuctionBid;
import com.rocketcrew.pocat.domain.bid.enums.BidStatus;
import com.rocketcrew.pocat.domain.bid.repository.AuctionBidRepository;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.AuctionException;
import com.rocketcrew.pocat.internal.testscenario.auction.dto.AuctionCloseWithoutAutoPaymentResponse;
import com.rocketcrew.pocat.internal.testscenario.auction.dto.AuctionExpirationInjectionResponse;
import com.rocketcrew.pocat.internal.testscenario.auction.dto.AuctionExpirationScheduleResponse;
import com.rocketcrew.pocat.internal.testscenario.support.TestScenarioGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionTestScenarioService {

    private static final String NEXT_STEP_TEMPLATE = "POST /internal/auctions/%d/close-expired";
    private static final String AUCTION_LOCK_KEY_PREFIX = "auction:lock:";
    private static final long AUCTION_LOCK_WAIT_SECONDS = 0L;

    private final AuctionRepository auctionRepository;
    private final AuctionBidRepository auctionBidRepository;
    private final OrderRepository orderRepository;
    private final AuctionExpirationRedisService auctionExpirationRedisService;
    private final AuctionEsIndexService auctionEsIndexService;
    private final RedissonClient redissonClient;
    private final TestScenarioGuard testScenarioGuard;

    @Transactional
    public AuctionExpirationInjectionResponse makeExpired(Long auctionId) {
        testScenarioGuard.ensureEnabled();

        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionException(ErrorCode.AUCTION_NOT_FOUND));
        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            throw new AuctionException(ErrorCode.AUCTION_NOT_ACTIVE);
        }

        LocalDateTime beforeEndedAt = auction.getEndedAt();
        LocalDateTime afterEndedAt = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(5);

        int updated = auctionRepository.updateEndedAtByIdAndStatus(auctionId, AuctionStatus.ACTIVE, afterEndedAt);
        if (updated != 1) {
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATUS_TRANSITION);
        }
        auctionExpirationRedisService.deleteExpirationKeys(auctionId);

        log.info("[TEST_SCENARIO] 경매 만료 시각 주입 auctionId={} beforeEndedAt={} afterEndedAt={}",
                auctionId, beforeEndedAt, afterEndedAt);

        return new AuctionExpirationInjectionResponse(
                auctionId,
                AuctionStatus.ACTIVE,
                beforeEndedAt,
                afterEndedAt,
                true,
                NEXT_STEP_TEMPLATE.formatted(auctionId)
        );
    }

    @Transactional
    public AuctionExpirationScheduleResponse scheduleExpiration(Long auctionId, long ttlSeconds) {
        testScenarioGuard.ensureEnabled();
        if (ttlSeconds < 10 || ttlSeconds > 3600) {
            throw new AuctionException(ErrorCode.INVALID_INPUT);
        }

        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionException(ErrorCode.AUCTION_NOT_FOUND));
        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            throw new AuctionException(ErrorCode.AUCTION_NOT_ACTIVE);
        }

        LocalDateTime beforeEndedAt = auction.getEndedAt();
        LocalDateTime afterEndedAt = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(ttlSeconds);

        int updated = auctionRepository.updateEndedAtByIdAndStatus(auctionId, AuctionStatus.ACTIVE, afterEndedAt);
        if (updated != 1) {
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATUS_TRANSITION);
        }
        auctionExpirationRedisService.setExpirationKeys(auctionId, afterEndedAt);

        log.info("[TEST_SCENARIO] 경매 Redis 만료 예약 주입 auctionId={} beforeEndedAt={} afterEndedAt={} ttlSeconds={}",
                auctionId, beforeEndedAt, afterEndedAt, ttlSeconds);

        return new AuctionExpirationScheduleResponse(
                auctionId,
                AuctionStatus.ACTIVE,
                beforeEndedAt,
                afterEndedAt,
                ttlSeconds,
                true,
                "Redis key expiration should trigger AuctionExpirationRedisSubscriber and close the auction automatically."
        );
    }

    @Transactional
    public AuctionCloseWithoutAutoPaymentResponse closeExpiredWithoutAutoPayment(Long auctionId) {
        testScenarioGuard.ensureEnabled();

        RLock lock = acquireAuctionLock(auctionId);
        releaseLockAfterTransaction(lock);

        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionException(ErrorCode.AUCTION_NOT_FOUND));
        if (!isClosable(auction)) {
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATUS_TRANSITION);
        }

        List<AuctionBid> bids = auctionBidRepository.findAllByAuctionId(auctionId);
        if (auction.getHighestBidderId() == null) {
            auction.markNoBidder();
            auctionExpirationRedisService.deleteExpirationKeys(auctionId);
            updateSearchStatusAfterCommit(auction.getId(), AuctionStatus.NO_BIDDER);

            log.info("[TEST_SCENARIO] auction closed without auto payment, no bidder auctionId={}", auctionId);
            return new AuctionCloseWithoutAutoPaymentResponse(
                    auctionId,
                    AuctionStatus.NO_BIDDER,
                    null,
                    null,
                    null,
                    null,
                    false,
                    false,
                    true,
                    "No winner exists, so no order is created."
            );
        }

        markBidResults(bids, auction.getHighestBidderId());
        auction.end();
        auctionExpirationRedisService.deleteExpirationKeys(auctionId);
        updateSearchStatusAfterCommit(auction.getId(), AuctionStatus.ENDED);

        Order order = orderRepository.findByAuctionIdAndBidderRank(auctionId, 1)
                .orElseGet(() -> orderRepository.save(Order.fromAuction(
                        auction.getId(),
                        auction.getCardId(),
                        auction.getSellerId(),
                        auction.getHighestBidderId(),
                        auction.getHighestPrice(),
                        1
                )));

        log.info("[TEST_SCENARIO] auction closed and order prepared without auto payment auctionId={} orderUid={}",
                auctionId, order.getOrderUid());

        return new AuctionCloseWithoutAutoPaymentResponse(
                auctionId,
                AuctionStatus.ENDED,
                auction.getHighestBidderId(),
                auction.getHighestPrice(),
                order.getOrderUid(),
                order.getStatus(),
                false,
                false,
                true,
                "POST /internal/test/payments/%s/auto-fail".formatted(order.getOrderUid())
        );
    }

    private boolean isClosable(Auction auction) {
        return auction.getStatus() == AuctionStatus.ACTIVE
                && auction.getEndedAt() != null
                && !auction.getEndedAt().isAfter(LocalDateTime.now(ZoneOffset.UTC));
    }

    private void markBidResults(List<AuctionBid> bids, Long winnerId) {
        for (AuctionBid bid : bids) {
            if (bid.getUserId().equals(winnerId) && bid.getStatus() == BidStatus.LEADING) {
                bid.markWon();
            } else if (bid.getStatus() == BidStatus.OUTBID) {
                bid.markLost();
            }
        }
    }

    private void updateSearchStatusAfterCommit(Long auctionId, AuctionStatus status) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                auctionEsIndexService.updateStatus(auctionId, status);
            }
        });
    }

    private RLock acquireAuctionLock(Long auctionId) {
        RLock lock = redissonClient.getLock(AUCTION_LOCK_KEY_PREFIX + auctionId);
        try {
            if (!lock.tryLock(AUCTION_LOCK_WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new AuctionException(ErrorCode.AUCTION_LOCK_FAILED);
            }
            return lock;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuctionException(ErrorCode.AUCTION_LOCK_FAILED, e);
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
