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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AuctionBidCommandService {

    private static final Duration BID_LOCK_TTL = Duration.ofSeconds(10);
    private static final String BID_LOCK_KEY_PREFIX = "auction:bid:lock:";

    private final AuctionBidRepository auctionBidRepository;
    private final AuctionQueryService auctionQueryService;
    private final UserQueryService userQueryService;
    private final EntityManager entityManager;
    private final StringRedisTemplate redisTemplate;

    public CreateAuctionBidResponse createBid(Long userId, Long auctionId, CreateBidRequest request) {
        if (request == null) {
            throw new BidException(ErrorCode.INVALID_INPUT);
        }

        User bidder = userQueryService.getUserEntity(userId);
        Auction auction = auctionQueryService.findAuctionEntityOrThrow(auctionId);

        validateAuctionAvailable(auction);
        validateBidder(bidder, auction);

        // Redis 분산락으로 해당 경매 락
        String lockKey = BID_LOCK_KEY_PREFIX + auctionId;
        String lockValue = UUID.randomUUID().toString();
        if (!acquireLock(lockKey, lockValue)) {
            throw new BidException(ErrorCode.BID_LOCK_FAILED);
        }
        releaseLockAfterTransaction(lockKey, lockValue);

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

        // 경매에서 기존 최고 입찰자의 입찰 찾기
        auctionBidRepository.findFirstByAuctionIdAndUserIdAndStatusOrderByBidPriceDescCreatedAtDesc(
                        auction.getId(),
                        previousHighestBidderId,
                        BidStatus.LEADING
                )
                .ifPresent(previousLeadingBid -> {
                    previousLeadingBid.markOutbid();
                    // TODO : 기존 최고입찰자에게 알림 발송을 위한 입찰 이벤트 카프카 발행 로직 필요
                });
    }

    private boolean acquireLock(String lockKey, String lockValue) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, BID_LOCK_TTL));
    }

    private void releaseLock(String lockKey, String lockValue) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(
                "if redis.call('get', KEYS[1]) == ARGV[1] then "
                        + "return redis.call('del', KEYS[1]) "
                        + "else return 0 end",
                Long.class
        );
        redisTemplate.execute(script, Collections.singletonList(lockKey), lockValue);
    }

    private void releaseLockAfterTransaction(String lockKey, String lockValue) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                releaseLock(lockKey, lockValue);
            }
        });
    }
}
