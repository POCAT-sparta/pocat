package com.rocketcrew.pocat.domain.auction.service;

import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.bid.entity.AuctionBid;
import com.rocketcrew.pocat.domain.bid.enums.BidStatus;
import com.rocketcrew.pocat.domain.bid.repository.AuctionBidRepository;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.AuctionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class AuctionBuyoutTransactionService {

    private final AuctionRepository auctionRepository;
    private final AuctionBidRepository auctionBidRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BuyoutReservation reserveBuyout(
            Long auctionId,
            User buyer,
            Consumer<Auction> validateBuyoutAvailable,
            BiConsumer<User, Auction> validateBuyer
    ) {
        Auction auction = findAuction(auctionId);
        validateBuyoutAvailable.accept(auction);
        validateBuyer.accept(buyer, auction);

        Long buyoutPrice = auction.getBuyoutPrice();
        auction.markPaymentPending();

        return new BuyoutReservation(
                auction.getId(),
                auction.getCardId(),
                auction.getSellerId(),
                buyoutPrice
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean restoreAuctionAfterPaymentFailure(Long auctionId) {
        Auction auction = findAuction(auctionId);
        if (auction.getStatus() == AuctionStatus.PAYMENT_PENDING) {
            auction.restoreActiveFromPaymentPending();
            return true;
        }
        return false;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BuyoutCompletion completeBuyout(
            BuyoutReservation reservation,
            Long buyerId,
            Order order,
            BuyoutCompletedEventPublisher buyoutCompletedEventPublisher
    ) {
        Auction auction = findAuction(reservation.auctionId());
        if (auction.getStatus() != AuctionStatus.PAYMENT_PENDING) {
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATUS_TRANSITION);
        }

        AuctionBid buyoutBid = findOrCreateWonBuyoutBid(auction, buyerId, reservation.buyoutPrice());
        Long previousHighestBidderId = auction.getHighestBidderId();

        markExistingBidsLost(auction, buyoutBid.getId());
        auction.updateHighestBid(reservation.buyoutPrice(), buyerId);
        auction.endAfterPaymentPending();
        buyoutCompletedEventPublisher.publish(
                auction,
                order,
                buyerId,
                previousHighestBidderId
        );

        return new BuyoutCompletion(auction, buyoutBid);
    }

    private AuctionBid findOrCreateWonBuyoutBid(Auction auction, Long buyerId, Long buyoutPrice) {
        return auctionBidRepository.findFirstByAuctionIdAndUserIdAndStatusOrderByBidPriceDescCreatedAtDesc(
                        auction.getId(),
                        buyerId,
                        BidStatus.WON
                )
                .orElseGet(() -> auctionBidRepository.save(AuctionBid.builder()
                        .auctionId(auction.getId())
                        .userId(buyerId)
                        .bidPrice(buyoutPrice)
                        .status(BidStatus.WON)
                        .build()));
    }

    private Auction findAuction(Long auctionId) {
        return auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionException(ErrorCode.AUCTION_NOT_FOUND));
    }

    private void markExistingBidsLost(Auction auction, Long buyoutBidId) {
        for (AuctionBid bid : auctionBidRepository.findAllByAuctionId(auction.getId())) {
            if (buyoutBidId.equals(bid.getId())) {
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

    @FunctionalInterface
    public interface BuyoutCompletedEventPublisher {
        void publish(Auction auction, Order order, Long buyerId, Long previousHighestBidderId);
    }
}
