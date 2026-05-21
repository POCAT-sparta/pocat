package com.rocketcrew.pocat.domain.auction.service;

import com.rocketcrew.pocat.domain.auction.dto.request.CreateAuctionRequest;
import com.rocketcrew.pocat.domain.auction.dto.request.AdminCancelAuctionRequest;
import com.rocketcrew.pocat.domain.auction.dto.request.InspectAuctionRequest;
import com.rocketcrew.pocat.domain.auction.dto.request.UpdateAuctionRequest;
import com.rocketcrew.pocat.domain.auction.dto.response.AdminCancelAuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.CancelAuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.CreateAuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.InspectAuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.UpdateAuctionResponse;
import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionInspectionResult;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.bid.enums.BidStatus;
import com.rocketcrew.pocat.domain.bid.repository.AuctionBidRepository;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.service.CardQueryService;
import com.rocketcrew.pocat.domain.user.service.UserQueryService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.AuctionException;
import com.rocketcrew.pocat.global.exception.domain.CardException;
import com.rocketcrew.pocat.global.exception.domain.UserException;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Transactional
public class AuctionCommandService {

    private static final String AUCTION_LOCK_KEY_PREFIX = "auction:lock:";
    private static final long AUCTION_LOCK_WAIT_SECONDS = 0L;

    private final AuctionRepository auctionRepository;
    private final AuctionBidRepository auctionBidRepository;
    private final CardQueryService cardQueryService;
    private final UserQueryService userQueryService;
    private final RedissonClient redissonClient;

    public CreateAuctionResponse createAuction(Long sellerId, CreateAuctionRequest request) {
        Card card = cardQueryService.validateRegistrableForAuction(request.cardId());
        validateBuyoutPrice(request.startingPrice(), request.buyoutPrice());
        Auction auction = Auction.builder()
                .cardId(request.cardId())
                .sellerId(sellerId)
                .title(request.title())
                .description(request.description())
                .cardImageUrl(card.getImageUrl())
                .startingPrice(request.startingPrice())
                .buyoutPrice(request.buyoutPrice())
                .status(AuctionStatus.PENDING)
                .build();
        return CreateAuctionResponse.from(auctionRepository.save(auction));
    }

    public UpdateAuctionResponse updateAuction(Long sellerId, Long id, UpdateAuctionRequest request) {
        Auction auction = auctionRepository.findById(id)
                .orElseThrow(() -> new AuctionException(ErrorCode.AUCTION_NOT_FOUND));
        validateSeller(auction, sellerId);
        validatePending(auction);
        validateUpdateRequest(request);
        Long startingPrice = request.startingPrice() != null ? request.startingPrice() : auction.getStartingPrice();
        Long buyoutPrice = request.buyoutPrice() != null ? request.buyoutPrice() : auction.getBuyoutPrice();
        validateBuyoutPrice(startingPrice, buyoutPrice);
        auction.update(request.title(), request.description(), request.startingPrice(), request.buyoutPrice());
        return UpdateAuctionResponse.from(auction);
    }

    public CancelAuctionResponse cancelAuction(Long sellerId, Long id) {
        Auction auction = auctionRepository.findById(id)
                .orElseThrow(() -> new AuctionException(ErrorCode.AUCTION_NOT_FOUND));
        validateSeller(auction, sellerId);
        validatePending(auction);
        auction.cancel(null);
        return CancelAuctionResponse.from(auction);
    }

    public AdminCancelAuctionResponse cancelAuction(Long adminId, Long id, AdminCancelAuctionRequest request) {
        Auction auction = auctionRepository.findById(id)
                .orElseThrow(() -> new AuctionException(ErrorCode.AUCTION_NOT_FOUND));
        validateAdminCancelReason(request.reason());
        validateCancellable(auction);

        RLock lock = redissonClient.getLock(AUCTION_LOCK_KEY_PREFIX + id);
        if (!acquireLock(lock)) {
            throw new AuctionException(ErrorCode.AUCTION_LOCK_FAILED);
        }
        releaseLockAfterTransaction(lock);

        Auction latestAuction = auctionRepository.findById(id)
                .orElseThrow(() -> new AuctionException(ErrorCode.AUCTION_NOT_FOUND));
        validateCancellable(latestAuction);

        Set<Long> recipientIds = collectAdminCancelNotificationRecipients(latestAuction);
        cancelCurrentLeadingBid(latestAuction);
        latestAuction.cancelByAdmin(request.reason().trim());
        // TODO Publish auction force-cancel notifications to Kafka for seller and all bid recipients.
        // recipientIds contains seller + every bidder who should receive the event.

        return AdminCancelAuctionResponse.from(latestAuction);
    }

    public InspectAuctionResponse inspectAuction(Long adminId, Long id, InspectAuctionRequest request) {
        Auction auction = auctionRepository.findById(id)
                .orElseThrow(() -> new AuctionException(ErrorCode.AUCTION_NOT_FOUND));
        validateInspectable(auction);

        if (request.result() == AuctionInspectionResult.PASSED) {
            validateAuctionDataForInspection(auction);
            auction.approve(adminId, LocalDateTime.now());
            return InspectAuctionResponse.from(auction);
        }

        validateRejectReason(request.reason());
        auction.reject(adminId, LocalDateTime.now(), request.reason().trim());
        return InspectAuctionResponse.from(auction);
    }

    private void validateSeller(Auction auction, Long sellerId) {
        if (!auction.getSellerId().equals(sellerId)) {
            throw new AuctionException(ErrorCode.USER_FORBIDDEN);
        }
    }

    private void validatePending(Auction auction) {
        if (auction.getStatus() != AuctionStatus.PENDING) {
            throw new AuctionException(ErrorCode.AUCTION_NOT_PENDING);
        }
    }

    private void validateCancellable(Auction auction) {
        if (auction.getStatus() == AuctionStatus.CANCELLED
                || auction.getStatus() == AuctionStatus.REJECTED
                || auction.getStatus() == AuctionStatus.ENDED
                || auction.getStatus() == AuctionStatus.NO_BIDDER) {
            throw new AuctionException(ErrorCode.AUCTION_CANNOT_CANCEL);
        }
    }


    private void validateInspectable(Auction auction) {
        if (auction.getStatus() != AuctionStatus.PENDING
                && auction.getStatus() != AuctionStatus.INSPECTING) {
            throw new AuctionException(ErrorCode.AUCTION_NOT_INSPECTING);
        }
    }

    private void validateAuctionDataForInspection(Auction auction) {
        validateSellerExists(auction.getSellerId());
        validateCardRegistrable(auction.getCardId());
        validateTitle(auction.getTitle());
        validateStartingPrice(auction.getStartingPrice());
        validateBuyoutPrice(auction.getStartingPrice(), auction.getBuyoutPrice());
    }

    private void validateSellerExists(Long sellerId) {
        try {
            userQueryService.getUserEntity(sellerId);
        } catch (UserException e) {
            throw new AuctionException(ErrorCode.AUCTION_SELLER_NOT_FOUND);
        }
    }

    private void validateCardRegistrable(Long cardId) {
        try {
            cardQueryService.validateRegistrableForAuction(cardId);
        } catch (CardException e) {
            if (e.getErrorCode() == ErrorCode.CARD_NOT_ACTIVE) {
                throw new AuctionException(ErrorCode.AUCTION_CARD_NOT_ACTIVE);
            }
            throw new AuctionException(ErrorCode.AUCTION_CARD_NOT_FOUND);
        }
    }

    private void validateTitle(String title) {
        if (!StringUtils.hasText(title) || title.length() > 255) {
            throw new AuctionException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateStartingPrice(Long startingPrice) {
        if (startingPrice == null || startingPrice <= 0) {
            throw new AuctionException(ErrorCode.AUCTION_PRICE_INVALID);
        }
    }

    private void validateRejectReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new AuctionException(ErrorCode.AUCTION_REASON_REQUIRED);
        }
    }

    private void validateAdminCancelReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new AuctionException(ErrorCode.AUCTION_REASON_REQUIRED);
        }
    }

    private Set<Long> collectAdminCancelNotificationRecipients(Auction auction) {
        Set<Long> recipientIds = new LinkedHashSet<>();
        recipientIds.add(auction.getSellerId());
        recipientIds.addAll(auctionBidRepository.findDistinctBidderIdsByAuctionId(auction.getId()));

        return recipientIds;
    }

    private void cancelCurrentLeadingBid(Auction auction) {
        auctionBidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtDesc(
                        auction.getId(),
                        BidStatus.LEADING
                )
                .ifPresent(previousLeadingBid -> {
                    previousLeadingBid.cancel();
                });
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

    private void validateUpdateRequest(UpdateAuctionRequest request) {
        if (request.title() == null
                && request.description() == null
                && request.startingPrice() == null
                && request.buyoutPrice() == null) {
            throw new AuctionException(ErrorCode.AUCTION_UPDATE_EMPTY);
        }
    }

    private void validateBuyoutPrice(Long startingPrice, Long buyoutPrice) {
        if (startingPrice == null || buyoutPrice == null) {
            return;
        }
        if (buyoutPrice <= startingPrice) {
            throw new AuctionException(ErrorCode.AUCTION_PRICE_INVALID);
        }
    }
}
