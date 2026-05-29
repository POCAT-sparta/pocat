package com.rocketcrew.pocat.domain.auction.service;

import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.event.AuctionActivatedEvent;
import com.rocketcrew.pocat.domain.auction.event.AuctionEndedEvent;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.bid.entity.AuctionBid;
import com.rocketcrew.pocat.domain.bid.enums.BidStatus;
import com.rocketcrew.pocat.domain.bid.repository.AuctionBidRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.AuctionException;
import com.rocketcrew.pocat.global.event.BaseEvent;
import com.rocketcrew.pocat.global.outbox.service.OutboxEventWriter;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Transactional
public class AuctionLifecycleService {

    private static final String AUCTION_LOCK_KEY_PREFIX = "auction:lock:";
    private static final long AUCTION_LOCK_WAIT_SECONDS = 0L;
    private static final int AUCTION_DURATION_DAYS = 3;
    private static final ZoneId AUCTION_ZONE = ZoneId.of("Asia/Seoul");

    private final AuctionRepository auctionRepository;
    private final AuctionBidRepository auctionBidRepository;
    private final RedissonClient redissonClient;
    private final ApplicationEventPublisher eventPublisher;
    private final AuctionEsIndexService auctionEsIndexService;
    private final OutboxEventWriter outboxEventWriter;

    // 검수 승인된 경매를 현재 시각 기준으로 ACTIVE 상태로 전환하고 종료 이벤트 예약용 정보를 확정한다.
    public boolean activateApprovedAuction(Long auctionId) {
        RLock lock = acquireAuctionLock(auctionId);
        releaseLockAfterTransaction(lock);

        Auction latestAuction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionException(ErrorCode.AUCTION_NOT_FOUND));
        if (latestAuction.getStatus() != AuctionStatus.APPROVED) {
            return false;
        }

        LocalDateTime startedAt = LocalDateTime.now(AUCTION_ZONE);
        LocalDateTime endedAt = startedAt.plusDays(AUCTION_DURATION_DAYS);
        latestAuction.activate(startedAt, endedAt);

        // ACTIVE 전환 후 커밋이 완료되면 ES 인덱싱
        final Auction auctionSnapshot = latestAuction;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                auctionEsIndexService.index(auctionSnapshot);
            }
        });

        publishAuctionActivatedEvent(
                latestAuction.getId(),
                latestAuction.getSellerId(),
                latestAuction.getEndedAt()
        );
        return true;
    }

    // 종료 시각이 지난 ACTIVE 경매를 낙찰 또는 유찰 상태로 한 번만 마감한다.
    public boolean closeExpiredAuction(Long auctionId) {
        RLock lock = acquireAuctionLock(auctionId);
        releaseLockAfterTransaction(lock);

        Auction latestAuction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionException(ErrorCode.AUCTION_NOT_FOUND));
        if (!isClosable(latestAuction)) {
            return false;
        }

        List<AuctionBid> bids = auctionBidRepository.findAllByAuctionId(auctionId);
        if (latestAuction.getHighestBidderId() == null) {
            latestAuction.markNoBidder();
            publishAuctionEndedEvent(latestAuction, List.of());
            final Long noAuctionId = latestAuction.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    auctionEsIndexService.updateStatus(noAuctionId, com.rocketcrew.pocat.domain.auction.enums.AuctionStatus.NO_BIDDER);
                }
            });
            return true;
        }

        List<Long> loserIds = bids.stream()
                .filter(bid -> bid.getStatus() == BidStatus.OUTBID)
                .map(AuctionBid::getUserId)
                .distinct()
                .toList();

        markBidResults(bids, latestAuction.getHighestBidderId());
        latestAuction.end();
        publishAuctionEndedEvent(latestAuction, loserIds);
        final Long endedAuctionId = latestAuction.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                auctionEsIndexService.updateStatus(endedAuctionId, com.rocketcrew.pocat.domain.auction.enums.AuctionStatus.ENDED);
            }
        });
        return true;
    }

    // 종료 처리가 가능한 ACTIVE 상태와 종료 시각 경과 여부를 검증한다.
    private boolean isClosable(Auction auction) {
        return auction.getStatus() == AuctionStatus.ACTIVE
                && auction.getEndedAt() != null
                && !auction.getEndedAt().isAfter(LocalDateTime.now(AUCTION_ZONE));
    }

    // 최종 최고 입찰자는 WON, 이미 최고가 갱신으로 밀린 OUTBID 입찰자만 LOST 상태로 정리한다.
    private void markBidResults(List<AuctionBid> bids, Long winnerId) {
        for (AuctionBid bid : bids) {
            if (bid.getUserId().equals(winnerId) && bid.getStatus() == BidStatus.LEADING) {
                bid.markWon();
            } else if (bid.getStatus() == BidStatus.OUTBID) {
                bid.markLost();
            }
        }
    }

    // 경매 종료 후속 처리를 Kafka consumer가 수행할 수 있도록 도메인 이벤트를 발행한다.
    private void publishAuctionActivatedEvent(Long auctionId, Long sellerId, LocalDateTime endedAt) {
        publishAuctionEvent(auctionId, new AuctionActivatedEvent(
                auctionId,
                sellerId,
                endedAt
        ));
    }

    private void publishAuctionEndedEvent(Auction auction, List<Long> loserIds) {
        publishAuctionEvent(auction.getId(), new AuctionEndedEvent(
                auction.getId(),
                auction.getHighestBidderId(),
                auction.getSellerId(),
                loserIds,
                auction.getHighestPrice()
        ));
    }

    private void publishAuctionEvent(Long auctionId, BaseEvent event) {
        outboxEventWriter.write("auction", String.valueOf(auctionId), event);
        eventPublisher.publishEvent(event);
    }

    // 같은 경매를 Redis 리스너와 스케줄러가 동시에 처리하지 못하도록 분산 락을 획득한다.
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

    // 트랜잭션이 끝난 뒤 현재 스레드가 가진 분산 락을 해제한다.
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
