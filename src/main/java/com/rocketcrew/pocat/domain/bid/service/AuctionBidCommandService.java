package com.rocketcrew.pocat.domain.bid.service;

import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.service.AuctionQueryService;
import com.rocketcrew.pocat.domain.bid.dto.request.CreateBidRequest;
import com.rocketcrew.pocat.domain.bid.dto.response.CreateAuctionBidResponse;
import com.rocketcrew.pocat.domain.bid.entity.AuctionBid;
import com.rocketcrew.pocat.domain.bid.enums.BidStatus;
import com.rocketcrew.pocat.domain.bid.repository.AuctionBidRepository;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.service.UserQueryService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.AuctionException;
import com.rocketcrew.pocat.global.exception.domain.BidException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Transactional
public class AuctionBidCommandService {

    private static final String BID_LOCK_KEY_PREFIX = "auction:bid:lock:";
    private static final long BID_LOCK_WAIT_SECONDS = 0L;

    private final AuctionBidRepository auctionBidRepository;
    private final AuctionQueryService auctionQueryService;
    private final UserQueryService userQueryService;
    private final EntityManager entityManager;
    private final RedissonClient redissonClient;

    public CreateAuctionBidResponse createBid(Long userId, Long auctionId, CreateBidRequest request) {
        if (request == null) {
            throw new BidException(ErrorCode.INVALID_INPUT);
        }

        User bidder = userQueryService.getUserEntity(userId);
        Auction auction = auctionQueryService.findAuctionEntityOrThrow(auctionId);

        validateAuctionAvailable(auction);
        validateBidder(bidder, auction);

        RLock lock = redissonClient.getLock(BID_LOCK_KEY_PREFIX + auctionId);
        if (!acquireLock(lock)) {
            throw new BidException(ErrorCode.BID_LOCK_FAILED);
        }
        releaseLockAfterTransaction(lock);

        entityManager.detach(auction);
        Auction latestAuction = auctionQueryService.findAuctionEntityOrThrow(auctionId);

        validateAuctionAvailable(latestAuction);
        validateBidder(bidder, latestAuction);
        validateNotCurrentHighestBidder(userId, latestAuction);
        validateBidPrice(request.bidPrice(), latestAuction);

        markPreviousLeadingBidAsOutbid(latestAuction);

        AuctionBid auctionBid = AuctionBid.builder()
                .userId(userId)
                .auctionId(auctionId)
                .bidPrice(request.bidPrice())
                .status(BidStatus.LEADING)
                .build();
        AuctionBid savedBid = auctionBidRepository.save(auctionBid);

        latestAuction.updateHighestBid(request.bidPrice(), userId);

        return CreateAuctionBidResponse.from(savedBid);
    }

    private void validateAuctionAvailable(Auction auction) {
        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            throw new AuctionException(ErrorCode.AUCTION_NOT_ACTIVE);
        }

        LocalDateTime now = LocalDateTime.now();
        if (auction.getStartedAt() == null || auction.getEndedAt() == null) {
            throw new AuctionException(ErrorCode.AUCTION_NOT_ACTIVE);
        }

        boolean isWithinAuctionTime = !now.isBefore(auction.getStartedAt())
                && now.isBefore(auction.getEndedAt());
        if (!isWithinAuctionTime) {
            throw new AuctionException(ErrorCode.AUCTION_NOT_ACTIVE);
        }
    }

    private void validateBidder(User bidder, Auction auction) {
        if (bidder.isBidBlocked()) {
            throw new BidException(ErrorCode.BID_BLOCKED_USER);
        }
        if (auction.getSellerId().equals(bidder.getId())) {
            throw new BidException(ErrorCode.BID_SELLER_FORBIDDEN);
        }
        if (!StringUtils.hasText(bidder.getBillingKey())) {
            throw new BidException(ErrorCode.BID_BILLING_KEY_REQUIRED);
        }
    }

    private void validateNotCurrentHighestBidder(Long bidderId, Auction auction) {
        if (auction.getHighestBidderId() != null && auction.getHighestBidderId().equals(bidderId)) {
            throw new BidException(ErrorCode.BID_ALREADY_LEADING);
        }
    }

    private void validateBidPrice(Long bidPrice, Auction auction) {
        if (bidPrice == null) {
            throw new BidException(ErrorCode.INVALID_INPUT);
        }

        Long buyoutPrice = auction.getBuyoutPrice();
        if (buyoutPrice != null && bidPrice >= buyoutPrice) {
            throw new BidException(ErrorCode.BID_BUYOUT_PRICE_NOT_ALLOWED);
        }

        Long highestPrice = auction.getHighestPrice();
        if (bidPrice < auction.getStartingPrice()
                || (highestPrice != null && bidPrice <= highestPrice)) {
            throw new BidException(ErrorCode.BID_PRICE_TOO_LOW);
        }
    }

    private void markPreviousLeadingBidAsOutbid(Auction auction) {
        Long previousHighestBidderId = auction.getHighestBidderId();
        if (previousHighestBidderId == null) {
            return;
        }

        auctionBidRepository.findFirstByAuctionIdAndUserIdAndStatusOrderByBidPriceDescCreatedAtDesc(
                        auction.getId(),
                        previousHighestBidderId,
                        BidStatus.LEADING
                )
                .ifPresent(previousLeadingBid -> {
                    previousLeadingBid.markOutbid();
                    // TODO Publish bid outbid event to Kafka for previous highest bidder notification.
                });
    }

    private boolean acquireLock(RLock lock) {
        try {
            return lock.tryLock(BID_LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BidException(ErrorCode.BID_LOCK_FAILED);
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
